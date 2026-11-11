#!/usr/bin/env python3
"""Reference implementation of DiceBear v10's core, used to pin this app's Kotlin port.

The app draws the vendored styles in `app/src/main/assets/dicebear/` on the device
(`data/avatar/`), so its PRNG and option resolution must agree with the reference
implementation byte for byte - otherwise every avatar would differ from what
api.dicebear.com renders for the same seed. This script is that reference, written
from <https://www.dicebear.com/specification/implement-dicebear-core/> and checked
against the published JavaScript implementation (@dicebear/core 10.7.0).

Two modes:

    python3 tools/dicebear_parity_fixture.py write-fixture
        Regenerate `app/src/test/resources/dicebear/parity.json`, the fixture
        `DiceBearParityTest` asserts the Kotlin resolver against.

    python3 tools/dicebear_parity_fixture.py verify
        Fetch the reference SVG for every vendored style and a few seeds from
        api.dicebear.com and diff it against the SVG this script generates. This
        is the only mode that needs the network; it is not part of the test suite.

Standard library only, deliberately: this file is committed next to the fixture so
the fixture can be regenerated and audited without installing anything.

Two deliberate simplifications, neither of which can affect the vendored styles:

* WCAG luminance is computed from the formula instead of a 256-entry lookup table.
  The reference embeds the table because `pow` is not correctly rounded and it needs
  bit-identical doubles across languages; here the luminance is only ever used to
  *order* candidates, and the vendored palettes are two-colour (black/white against a
  pastel), which no last-bit difference can reorder.
* Only the option subset the app exposes is resolved: seed and the style's own
  defaults. Size, title, flip, rotate, scale, borderRadius, translate and the user
  colour/variant overrides are not implemented because the app never sets them.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
STYLES_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "dicebear"
FIXTURE_PATH = REPO_ROOT / "app" / "src" / "test" / "resources" / "dicebear" / "parity.json"

STYLES = [
    "identicon",
    "line-face",
    "lorelei-neutral",
    "rings",
    "shape-grid",
    "shadows",
    "shapes",
    "stripes",
    "triangles",
    "weave",
]

# ASCII, a phone number, a non-Latin-1 name, a non-BMP emoji (two UTF-16 code units,
# so it pins the FNV-1a iteration unit) and the empty seed.
SEEDS = ["Ada", "+33612345678", "ada@example.com", "Zoë", "🤖", ""]

API_ROOT = "https://api.dicebear.com/10.x"


# --------------------------------------------------------------------------------------
# PRNG
# --------------------------------------------------------------------------------------


def utf16_code_units(text: str) -> list[int]:
    """FNV-1a iterates UTF-16 code units, not code points: an emoji hashes as two units."""
    data = text.encode("utf-16-le")
    return [data[i] | (data[i + 1] << 8) for i in range(0, len(data), 2)]


def fnv1a(text: str) -> int:
    value = 0x811C9DC5
    for unit in utf16_code_units(text):
        value ^= unit
        value = (value * 0x01000193) & 0xFFFFFFFF
    return value


def fnv1a_hex(text: str) -> str:
    return f"{fnv1a(text):08x}"


class Mulberry32:
    """Stateful PRNG; the only DiceBear selection method that advances state is `shuffle`."""

    def __init__(self, seed: int) -> None:
        self.state = seed & 0xFFFFFFFF

    def next_uint32(self) -> int:
        self.state = (self.state + 0x6D2B79F5) & 0xFFFFFFFF
        z = self.state
        t = ((z ^ (z >> 15)) * (z | 1)) & 0xFFFFFFFF
        t = (t ^ (t + (((t ^ (t >> 7)) * (t | 61)) & 0xFFFFFFFF))) & 0xFFFFFFFF
        return (t ^ (t >> 14)) & 0xFFFFFFFF

    def next_float(self) -> float:
        return self.next_uint32() / 4294967296.0


def js_round(value: float) -> int:
    """JavaScript's Math.round: halves go toward +Infinity, unlike Python's round()."""
    floor = int(value // 1)
    return floor + 1 if value - floor >= 0.5 else floor


class Prng:
    def __init__(self, seed: str) -> None:
        self.seed = seed

    def get_value(self, key: str) -> float:
        return Mulberry32(fnv1a(f"{self.seed}:{key}")).next_float()

    def pick(self, key: str, items: list[str]) -> str | None:
        if not items:
            return None
        if len(items) == 1:
            return items[0]
        unique = _unique(items)
        if len(unique) == 1:
            return unique[0]
        ordered = sorted(unique)
        return ordered[int(self.get_value(key) * len(ordered))]

    def weighted_pick(self, key: str, weights: dict[str, int]) -> str | None:
        if not weights:
            return None
        if len(weights) == 1:
            return next(iter(weights))
        ordered = sorted(weights)
        total = sum(weights[name] for name in ordered)
        if total == 0:
            return self.pick(key, ordered)
        threshold = self.get_value(key) * total
        cumulative = 0
        for name in ordered:
            cumulative += weights[name]
            if threshold < cumulative:
                return name
        return ordered[-1]

    def boolean(self, key: str, likelihood: float = 50) -> bool:
        return self.get_value(key) * 100 < likelihood

    def float_in(self, key: str, minimum: float, maximum: float, step: float | None = None) -> float:
        low, high = min(minimum, maximum), max(minimum, maximum)
        if step is not None and step > 0:
            buckets = int((high - low) / step) + 1
            raw = low + int(self.get_value(key) * buckets) * step
        else:
            raw = low + self.get_value(key) * (high - low)
        return js_round(raw * 10000) / 10000

    def integer_in(self, key: str, minimum: float, maximum: float) -> int:
        low, high = min(minimum, maximum), max(minimum, maximum)
        return int(self.get_value(key) * (high - low + 1)) + int(low)

    def shuffle(self, key: str, items: list[str]) -> list[str]:
        if len(items) <= 1:
            return list(items)
        result = sorted(_unique(items))
        prng = Mulberry32(fnv1a(f"{self.seed}:{key}"))
        for i in range(len(result) - 1, 0, -1):
            j = int(prng.next_float() * (i + 1))
            result[i], result[j] = result[j], result[i]
        return result


def _unique(items: list[str]) -> list[str]:
    """Deduplicate by string representation, keeping the first occurrence."""
    seen: set[str] = set()
    result = []
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


# --------------------------------------------------------------------------------------
# Colours
# --------------------------------------------------------------------------------------


def to_hex(value: str) -> str:
    raw = value[1:].lower() if value.startswith("#") else value.lower()
    if len(raw) == 3:
        return "#" + raw[0] * 2 + raw[1] * 2 + raw[2] * 2
    if len(raw) == 4:
        return "#" + raw[0] * 2 + raw[1] * 2 + raw[2] * 2 + raw[3] * 2
    return "#" + raw


def to_rgb_hex(value: str) -> str:
    normalised = to_hex(value)
    return normalised[:7] if len(normalised) > 7 else normalised


def _linearize(channel: int) -> float:
    s = channel / 255
    return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4


def luminance(value: str) -> float:
    raw = to_hex(value)[1:]
    return (
        0.2126 * _linearize(int(raw[0:2], 16))
        + 0.7152 * _linearize(int(raw[2:4], 16))
        + 0.0722 * _linearize(int(raw[4:6], 16))
    )


def sort_by_contrast(candidates: list[str], reference: str) -> list[str]:
    reference_luminance = luminance(reference)

    def ratio(candidate: str) -> float:
        candidate_luminance = luminance(candidate)
        return (max(candidate_luminance, reference_luminance) + 0.05) / (
            min(candidate_luminance, reference_luminance) + 0.05
        )

    return sorted(candidates, key=lambda candidate: -ratio(candidate))


def filter_not_equal_to(candidates: list[str], excluded: list[str]) -> list[str]:
    blocked = {to_rgb_hex(value) for value in excluded}
    filtered = [candidate for candidate in candidates if to_rgb_hex(candidate) not in blocked]
    return filtered if filtered else list(candidates)


# --------------------------------------------------------------------------------------
# Number formatting (only needed for the SVG diff, not for the fixture)
# --------------------------------------------------------------------------------------


def format_number(value: float) -> str:
    scaled = js_round(value * 100000)
    sign = "-" if scaled < 0 else ""
    scaled = abs(scaled)
    integer_part = scaled // 100000
    fraction = str(scaled % 100000).rjust(5, "0").rstrip("0")
    return f"{sign}{integer_part}{'.' + fraction if fraction else ''}"


# --------------------------------------------------------------------------------------
# Resolution
# --------------------------------------------------------------------------------------


class Definition:
    """A style definition plus the option resolution for one seed."""

    def __init__(self, data: dict, seed: str) -> None:
        self.data = data
        self.seed = seed
        self.prng = Prng(seed)
        self._components: dict[str, dict] = {}
        self._colors: dict[str, list[str]] = {}
        self._color_stack: list[str] = []

    # -- components ------------------------------------------------------------------

    def _source(self, name: str) -> dict:
        """Aliases (`extends`) inherit dimensions, variants and transforms from the source."""
        component = self.data.get("components", {}).get(name, {})
        if "extends" in component:
            return self.data["components"][component["extends"]]
        return component

    def source_name(self, name: str) -> str:
        component = self.data.get("components", {}).get(name, {})
        return component.get("extends", name)

    def component_visible(self, name: str) -> bool:
        probability = self._source(name).get("probability", 100)
        return self.prng.boolean(f"{name}Probability", probability)

    def component_variant(self, name: str) -> str | None:
        if not self.component_visible(name):
            return None
        variants = self._source(name).get("variants", {})
        weights = {variant: details.get("weight", 1) for variant, details in variants.items()}
        return self.prng.weighted_pick(f"{name}Variant", weights)

    def component_transform(self, name: str) -> dict[str, float]:
        component = self._source(name)
        rotate = component.get("rotate")
        scale = component.get("scale")
        translate = component.get("translate", {})
        return {
            "rotate": self.prng.float_in(
                f"{name}Rotate", rotate["min"], rotate["max"], rotate.get("step")
            )
            if rotate
            else 0,
            "translateX": self.prng.float_in(
                f"{name}TranslateX",
                translate["x"]["min"],
                translate["x"]["max"],
                translate["x"].get("step"),
            )
            if "x" in translate
            else 0,
            "translateY": self.prng.float_in(
                f"{name}TranslateY",
                translate["y"]["min"],
                translate["y"]["max"],
                translate["y"].get("step"),
            )
            if "y" in translate
            else 0,
            "scale": self.prng.float_in(f"{name}Scale", scale["min"], scale["max"], scale.get("step"))
            if scale
            else 1,
        }

    def components(self) -> dict[str, dict]:
        if self._components:
            return self._components
        for name in self.data.get("components", {}):
            variant = self.component_variant(name)
            transform = self.component_transform(name)
            self._components[name] = {
                "visible": variant is not None,
                "variant": variant,
                **transform,
            }
        return self._components

    # -- colours ---------------------------------------------------------------------

    def color(self, name: str) -> list[str]:
        if name in self._colors:
            return self._colors[name]
        if name in self._color_stack:
            raise ValueError(f"circular colour reference: {self._color_stack + [name]}")
        style_color = self.data.get("colors", {}).get(name)
        candidates = [to_hex(value) for value in (style_color or {}).get("values", [])]
        if style_color is None:
            # No palette and no user colours: nothing to draw for this group.
            self._colors[name] = []
            return []
        self._color_stack.append(name)
        try:
            contrast_to = style_color.get("contrastTo")
            if contrast_to:
                reference = self.color(contrast_to)
                if reference:
                    candidates = sort_by_contrast(candidates, reference[0])
            not_equal_to = style_color.get("notEqualTo") or []
            if not_equal_to:
                excluded: list[str] = []
                for other in not_equal_to:
                    excluded.extend(self.color(other))
                candidates = filter_not_equal_to(candidates, excluded)
        finally:
            self._color_stack.pop()
        # A contrast sort must not be undone by the shuffle; everything else is shuffled.
        ordered = candidates if contrast_to else self.prng.shuffle(f"{name}Color", candidates)
        self._colors[name] = ordered[:1]
        return self._colors[name]

    def colors(self) -> dict[str, list[str]]:
        groups = {"background", *self.data.get("colors", {})}
        return {name: self.color(name) for name in sorted(groups)}


# --------------------------------------------------------------------------------------
# SVG generation (verification only)
# --------------------------------------------------------------------------------------


class SvgRenderer:
    def __init__(self, definition: Definition) -> None:
        self.definition = definition
        self.defs: dict[str, str] = {}

    @property
    def seed_hash(self) -> str:
        source = self.definition.data.get("meta", {}).get("source", {}).get("name", "")
        return fnv1a_hex(f"{source}:{self.definition.seed}")

    def render(self) -> str:
        canvas = self.definition.data["canvas"]
        width = float(canvas["width"])
        height = float(canvas["height"])
        background = ""
        background_colors = self.definition.color("background")
        if background_colors:
            background = (
                f'<rect width="{format_number(width)}" height="{format_number(height)}" '
                f'fill="{background_colors[0]}"/>'
            )
        body = self.render_elements(canvas["elements"])
        body = f'<g clip-path="url(#clip-{self.seed_hash})">{background}{body}</g>'
        self.defs[f"clip-{self.seed_hash}"] = (
            f'<clipPath id="clip-{self.seed_hash}"><rect width="{format_number(width)}" '
            f'height="{format_number(height)}" rx="0" ry="0"/></clipPath>'
        )
        attributes = render_attributes(self.definition.data.get("attributes", {}), self.definition)
        defs = f"<defs>{''.join(self.defs.values())}</defs>" if self.defs else ""
        return (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {format_number(width)} '
            f'{format_number(height)}"{attributes} aria-hidden="true">{defs}{body}</svg>'
        )

    def render_elements(self, elements: list[dict]) -> str:
        return "".join(self.render_element(element) for element in elements)

    def render_element(self, element: dict) -> str:
        kind = element.get("type")
        if kind == "text":
            return element.get("value", "")
        if kind == "component":
            return self.render_component(element)
        name = element.get("name")
        if not name:
            return ""
        attributes = render_attributes(element.get("attributes", {}), self.definition)
        children = self.render_elements(element.get("children", []))
        if not children:
            return f"<{name}{attributes}/>"
        return f"<{name}{attributes}>{children}</{name}>"

    def render_component(self, element: dict) -> str:
        name = element.get("name")
        if not isinstance(name, str):
            return ""
        variant_name = self.definition.component_variant(name)
        if variant_name is None:
            return ""
        source = self.definition.source_name(name)
        identifier = f"{source}-{variant_name}-{self.seed_hash}"
        if identifier not in self.defs:
            variant = self.definition._source(name)["variants"][variant_name]
            self.defs[identifier] = f'<g id="{identifier}">{self.render_elements(variant["elements"])}</g>'
        transforms = self.build_transforms(name)
        attributes = dict(element.get("attributes", {}))
        if transforms:
            author = attributes.get("transform")
            parts = [author, *transforms] if author else transforms
            attributes["transform"] = " ".join(parts)
        return f'<use{render_attributes(attributes, self.definition)} href="#{identifier}"/>'

    def build_transforms(self, name: str) -> list[str]:
        transform = self.definition.components()[name]
        component = self.definition._source(name)
        if (
            transform["translateX"] == 0
            and transform["translateY"] == 0
            and transform["rotate"] == 0
            and transform["scale"] == 1
        ):
            return []
        width = float(component["width"])
        height = float(component["height"])
        parts = []
        if transform["translateX"] != 0 or transform["translateY"] != 0:
            parts.append(
                "translate({}, {})".format(
                    format_number(transform["translateX"] / 100 * width),
                    format_number(transform["translateY"] / 100 * height),
                )
            )
        if transform["rotate"] != 0:
            parts.append(
                "rotate({}, {}, {})".format(
                    format_number(transform["rotate"]),
                    format_number(width / 2),
                    format_number(height / 2),
                )
            )
        if transform["scale"] != 1:
            parts.append(
                "translate({}, {}) scale({}) translate({}, {})".format(
                    format_number(width / 2),
                    format_number(height / 2),
                    format_number(transform["scale"]),
                    format_number(-width / 2),
                    format_number(-height / 2),
                )
            )
        return parts


def render_attributes(attributes: dict, definition: Definition) -> str:
    if not attributes:
        return ""
    parts = []
    for key, value in attributes.items():
        if isinstance(value, dict):
            if value.get("type") == "color":
                colors = definition.color(value["name"])
                resolved = colors[0] if colors else "none"
            else:
                raise ValueError(f"variable attribute {key} is not implemented")
        else:
            resolved = value
        parts.append(f'{key}="{resolved}"')
    return " " + " ".join(parts)


# --------------------------------------------------------------------------------------
# Fixture
# --------------------------------------------------------------------------------------


def load_definition(style: str) -> dict:
    return json.loads((STYLES_DIR / f"{style}.json").read_text(encoding="utf-8"))


def build_fixture() -> dict:
    fixture: dict = {
        "generatedBy": "tools/dicebear_parity_fixture.py write-fixture",
        "stylesVersion": "@dicebear/styles 10.6.0",
        "seeds": SEEDS,
        "styles": {},
    }
    for style in STYLES:
        data = load_definition(style)
        fixture["styles"][style] = {
            seed: {
                "components": Definition(data, seed).components(),
                "colors": Definition(data, seed).colors(),
            }
            for seed in SEEDS
        }
    return fixture


def write_fixture() -> None:
    fixture = build_fixture()
    FIXTURE_PATH.parent.mkdir(parents=True, exist_ok=True)
    FIXTURE_PATH.write_text(json.dumps(fixture, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {FIXTURE_PATH.relative_to(REPO_ROOT)}")


# --------------------------------------------------------------------------------------
# Verification against the published API
# --------------------------------------------------------------------------------------


def normalise_svg(svg: str) -> str:
    """Drop the parts of the reference output this script does not reproduce."""
    import re

    svg = re.sub(r"<!--.*?-->", "", svg, flags=re.S)
    svg = re.sub(r"<metadata[^>]*>.*?</metadata>", "", svg, flags=re.S)
    return svg


def verify(styles: list[str], seeds: list[str]) -> int:
    failures = 0
    for style in styles:
        data = load_definition(style)
        for seed in seeds:
            url = f"{API_ROOT}/{style}/svg?seed={urllib.parse.quote(seed)}"
            with urllib.request.urlopen(url, timeout=30) as response:
                reference = normalise_svg(response.read().decode("utf-8"))
            ours = normalise_svg(SvgRenderer(Definition(data, seed)).render())
            if reference == ours:
                print(f"ok    {style:16} {seed!r}")
                continue
            failures += 1
            print(f"DIFF  {style:16} {seed!r}")
            import difflib

            for line in difflib.unified_diff(
                reference.split("><"), ours.split("><"), "api", "local", lineterm="", n=1
            ):
                print(f"      {line}")
    return failures


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["write-fixture", "verify"])
    parser.add_argument("--styles", nargs="*", default=STYLES)
    parser.add_argument("--seeds", nargs="*", default=["Ada", "+33612345678"])
    args = parser.parse_args(argv)
    if args.mode == "write-fixture":
        write_fixture()
        return 0
    return 1 if verify(args.styles, args.seeds) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
