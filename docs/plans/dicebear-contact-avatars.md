# DiceBear contact avatars

Status: implemented. Branch `feat/dicebear-contact-avatars`, PR #223.

Contact rows with no photo currently fall back to `Icons.Default.Person`. This
plan gives them a deterministic, offline, per-contact avatar, generated on the
device from a vendored DiceBear style definition.

**Note on branch policy.** `AGENTS.md` says this project is trunk-based and work
lands by pushing to `main`. The operator explicitly asked for a GitHub pull
request for this change, so the work lives on this branch and lands through a
PR. Everything else in `AGENTS.md` still applies (local checks before pushing,
schema exports, lint baseline, and so on).

## Why not the DiceBear HTTP API

- The seed would be a contact name: shipping it to a third party for a
  decoration contradicts the app's backup/encryption stance.
- A launcher must paint a contact list instantly and offline.
- The public API is "free to use for non-commercial purposes" with 50 rps
  (SVG) / 10 rps (raster) limits.

## Why not an SVG parser

A DiceBear v10 style is **JSON describing a scene graph**, not SVG text. The
only textual SVG in it is the path `d` string, and androidx already parses that
(`androidx.compose.ui.graphics.vector.PathParser`, `androidx.core.graphics.PathParser`).
So the app ships a small interpreter over the JSON and draws with `DrawScope`.
No new dependency, no XML, no CSS cascade.

Upstream spec this must follow, byte-for-byte on the selection side:
<https://www.dicebear.com/specification/implement-dicebear-core/>.

## Ordered steps

### Step 1 - core + renderer + samples + PR

1. PRNG (`data/avatar/`): FNV-1a over UTF-16 code units, Mulberry32,
   `getValue(seed, key) = mulberry32(fnv1a("$seed:$key")).nextFloat()`, and the
   selection helpers `pick`, `weightedPick`, `bool`, `float`, `integer`,
   `shuffle`. `shuffle` is the only stateful user of the PRNG.
2. Style model (`kotlinx.serialization`, already a dependency): `meta`,
   `canvas{elements,width,height}`, `components{extends,probability,variants{weight,elements}}`,
   `colors{values,contrastTo,notEqualTo}`, element kinds `element` / `component`
   / `text`, attributes that are a literal string, `{type:variable}` or
   `{type:color}`.
3. Resolver: component visibility + variant choice per seed; color palettes
   resolved through `shuffle` (not `pick`) and `notEqualTo` filtering. Alias
   (`extends`) rule: user options come from the source component, PRNG keys use
   the alias name.
4. Renderer (`ui/components/dicebear/`): scene walk into `DrawScope`.
   `PathParser` for `d`; `drawPath/drawCircle/drawOval/drawRect`; transforms via
   `withTransform`/`Matrix`; `FillType.EvenOdd` for `fill-rule`; `Stroke` with
   cap/join/miter; `fill-opacity`/`stroke-opacity`/`opacity`/group alpha via
   `saveLayer`; `mix-blend-mode` via `saveLayer` + `Paint.blendMode`; `mask` and
   `clip-path` via `clipPath`. Unsupported constructs must be reported, not
   silently dropped.
5. Tests:
   - `DiceBearParityTest` - resolved variant/color choices per seed against a
     committed fixture produced by a committed generator script.
   - `DiceBearRenderTest` (Robolectric, native graphics) - renders each vendored
     style to a PNG, compares against committed reference PNGs downloaded from
     `api.dicebear.com` at the same size with a tolerance, and asserts the same
     seed renders identically twice.
6. Samples: export a grid of styles x seeds to `docs/avatars/samples/`, alongside
   the reference images from the API, and open the PR with them in the body.

Acceptance: `./gradlew :app:testDevDebugUnitTest` green, `:app:assembleDevDebug`
green, samples committed, PR opened.

### Step 2 - settings selector

1. `ContactAvatarStyle` enum (`identicon`, `stripes`, `triangles`, `weave`,
   `rings`, `shape-grid`, `line-face`, `shadows`, `shapes`, `lorelei-neutral`,
   plus `none`), each mapping to `assets/dicebear/<id>.json`.
2. Preference `contacts.avatar.style`, default `shadows`, written through
   `SettingsRepository` like the other keys (test with
   `CoroutineScope(Dispatchers.Unconfined)`).
3. Stateless selector composable rendering live previews, wired into
   `SettingsScreen`, with a credits entry generated from the vendored
   definitions' `meta.license.text` (see `assets/dicebear/LICENSES.md`).

Acceptance: selecting a style changes the avatars live, survives process death,
and the selector holds on a 320dp/mdpi screen.

### Step 3 - contact rows

1. Add the raw contact display name to the row (the composed label carries the
   number-type suffix such as `Ada (Mobile)`, which must not reach the seed).
2. Seed = `rawDisplayName.trim().lowercase(Locale.ROOT)`; every phone number of
   one contact therefore shares one avatar. Fall back to the call URI only when
   the row has no name at all (a number-only contact has nothing to share).
3. `ApplicationIcon`: `icon ?: photo ?: DiceBearAvatar(seed, style)`, keeping
   `Icons.Default.Person` for style `none`.

Acceptance: unit test that two numbers of one contact produce the same seed;
`:app:testDevDebugUnitTest` and `:app:testProdDebugUnitTest` green.

## Renderer scope (measured against the vendored styles)

The 10 vendored styles are paths and strokes only - no mask, no gradient, no
filter, no `currentColor`, no `<text>` element. What they do use:

| Feature | Where | Mapping |
| --- | --- | --- |
| `path`, `circle`, `ellipse`, `rect`, `g` | all | `Path` + draw calls |
| `<use>`/component refs with `transform` (translate, rotate, matrix) | 8 styles | `withTransform` |
| path data | 10 styles, full grammar incl. arcs | `PathParser` |
| `fill` (hex, palette ref, `none`, `white`, `black`) | all | `Color` |
| `stroke` + width/cap/join | rings, line-face | `Stroke` |
| `fill-opacity`, `stroke-opacity`, `opacity` | weave, rings, stripes, triangles, shape-grid | alpha / `saveLayer` |
| `fill-rule`, `clip-rule` | shapes | `FillType.EvenOdd` |
| `style="mix-blend-mode:overlay\|lighten"` | stripes, triangles, rings, shape-grid | `saveLayer` + `Paint.blendMode` |
| `<style>` + `class` | shapes, line-face, stripes | ignore - animations only |

Masks, gradients and filters are out of scope for the vendored set but must be
detected and reported when a future style needs them.

## Assets

`app/src/main/assets/dicebear/*.json`, unmodified from `@dicebear/styles` 10.6.0,
87 KB total, all CC0 1.0. `LICENSES.md` in that directory is generated from the
definitions' own `meta.license.text` and must stay in sync.

## Notices

The renderer is a port of DiceBear v10's **MIT** core, so it owes the notice the
CC0 style definitions do not. `THIRD_PARTY_LICENSES.md` lists it under "Vendored
and ported works" - it has no POM, so no dependency scan would ever find it -
`NOTICE` points at that section, and the per-style text ships in the APK at
`assets/dicebear/LICENSES.md`. Two guards keep the pairing honest:
`LicenseAssetsTest` fails if a vendored style has no entry in that file, and
the release workflow's "Verify license and notices are packaged" step now checks
the style licenses too, not just the three files staged from the repository root.

## Risks

- Transform composition order and group alpha are the two places where a native
  renderer drifts from the reference; the pixel test is the guard.
- Reference PNG rasterisation (resvg) and Skia anti-alias differently, so the
  pixel test needs a tolerance, not exact equality.
- Robolectric must run with native graphics for `Bitmap`/`Path` drawing.

## What landed

- Selection parity is byte-identical for all ten styles and four seeds
  (`tools/dicebear_parity_fixture.py verify`, diffed against the API's SVG).
  Rasterisation differs only in anti-aliasing: seven styles at 0.00% of pixels
  off by more than 32, worst case `identicon` at 2.8% because the reference
  honours `shape-rendering="crispEdges"` and Skia always anti-aliases.
- Two premises of this plan were wrong and the API settled them: `contrastTo`
  **is** used (stripes, triangles, shape-grid pick their ink against the
  background), and `identicon`'s `row01` is the **top** row, not the bottom.
- `identicon`, `stripes`, `triangles`, `weave`, `rings`, `shape-grid`,
  `line-face`, `shadows`, `shapes`, `lorelei-neutral` are selectable in Settings
  (`contacts.avatar.style`, default `shadows`) with live previews and credits
  read from each definition's `meta.license.text`.
- Contacts without a photo draw an avatar seeded by their own name, so a contact
  with several numbers has one picture. `SettingsScreen`'s root also scrolls
  now: it never did, and the new block pushed the rows below it further past the
  cut.

## Deferred

- Masks, gradients, filters, `clip-path` and variable references (`initials`,
  `fontFamily`). Detected and rejected, so a future style that needs them fails
  loudly instead of drawing something subtly wrong.
- Per-avatar user options (`size`, `flip`, `rotate`, `scale`, colour and variant
  overrides). The app never sets them; the seeded defaults are what it draws.
- `initial-face`, `initials` and the illustrated styles that need masks; adding
  one is a data change once the renderer grows the missing construct.
