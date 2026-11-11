# DiceBear avatar samples

The ten vendored DiceBear styles, drawn by this app's on-device renderer
(`ui/components/dicebear/`) and by [api.dicebear.com](https://www.dicebear.com) for the
same seed, side by side.

- `<style>-<seed>.png` — this app's renderer, 200x200.
- `ref-<style>-<seed>.png` — the reference rasterisation from the HTTP API, 200x200.

The seeds are `Ada`, `+33612345678` and `🤖`. The emoji is deliberate: it is outside the
Basic Multilingual Plane, so it is two UTF-16 code units, and it would resolve to a
different avatar if the FNV-1a hash iterated code points instead.

## Regenerating

The app's own images:

```sh
./gradlew :app:testDevDebugUnitTest --tests '*DiceBearSampleExportTest*'
```

`DiceBearSampleExportTest` writes them with native Skia under Robolectric; it needs no
network and no device. It also rewrites them on every full test run, so a Skia change
after a Robolectric bump shows up here as a reviewable diff.

The reference images need the network, which is why no test downloads them:

```sh
for style in identicon stripes triangles weave rings shape-grid line-face shadows shapes lorelei-neutral; do
  for seed in Ada +33612345678 🤖; do
    curl -G "https://api.dicebear.com/10.x/$style/png" \
      --data-urlencode "seed=$seed" --data-urlencode "size=200" \
      -o "docs/avatars/samples/ref-$style-$seed.png"
  done
done
```

## What differs, and why

Nothing visible. Comparing the pairs pixel by pixel, the worst case is identicon at 1.4%
of pixels off by more than 32 on some channel (mean channel delta 1.65); every other style
stays under 0.5% and below 1.0 mean. All of it is anti-aliasing: identicon sets
`shape-rendering="crispEdges"`, which makes the reference snap cell edges to whole pixels,
while this renderer always anti-aliases. The geometry, palettes and blend modes match
exactly — `DiceBearRenderTest` asserts that with a tolerance derived from those numbers.

The selection layer itself is verified byte for byte against the API's SVG output:
`python3 tools/dicebear_parity_fixture.py verify` regenerates the reference SVG for each
style and seed and diffs it against `api.dicebear.com/10.x/<style>/svg`, and the same
script writes the fixture `DiceBearParityTest` asserts against.
