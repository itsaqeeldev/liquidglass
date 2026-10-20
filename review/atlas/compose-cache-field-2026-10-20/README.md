# Compose cache and field maintenance

Candidate based on upstream `4cd5ed8` (20 October 2026), addressing issues 80–82. No shader,
material parameter, motion constant, resolution or published API change.

## Changes

- A remembered lease reference-counts views of the shared backdrop. Changing settings or leaving
  composition releases it; abandonment before effects run also releases the acquisition. Multiple
  active consumers share a view. Only active views are mirrored.
- Sampled-field keys include actual size and pad, so different inner geometry cannot collide on
  equal rounded padded dimensions.
- Shader-incapable backends skip field preparation.
- The full-resolution EDT uses reusable `inside`, `dx`, `dy`, distance and outer-distance buffers.
  The second transform reuses the first transform's working arrays; published bitmaps own their
  output. A single nonblocking pool retains up to 1,048,576 pixels of arrays (~17 MiB); larger or
  concurrent builds use temporary workspaces. The pool is shared across panels and is intentionally
  bounded, not a per-panel high-water allocation. It retains memory for the process lifetime.

## Paired CPU/allocation probe

Windows, Intel i7-10700, JBR/OpenJDK 25.0.3, Compose 1.12.2, Skiko 0.150.2. Direct field calls,
not shader rendering or presented frames. Baseline is the full-resolution implementation from
upstream `0d88a23`/Maven 1.0.0, with identifiers renamed to coexist in one test JVM.
New code uses the same rasterization, EDT arithmetic and encoding.

Notched closed path, 24 px pad, 48 px band, sizes in pixels. 40 warmups per algorithm/size, 25 samples,
alternating baseline/fresh/pooled and pooled/fresh/baseline order. Calling-thread Java allocations
from ThreadMXBean exclude native/GPU memory. Both new modes were compared pixel-for-pixel with the
baseline before timing. Fresh means a new workspace each call; pooled means already-grown arrays.

| Panel px | Mode | p50 ms | p95 ms | Java bytes/build (median) |
| --- | --- | ---: | ---: | ---: |
| 1080×300 | Baseline | 18.1797 | 21.2381 | 15,703,504 |
| 1080×300 | New, fresh | 18.0442 | 19.3340 | 10,993,040 |
| 1080×300 | New, pooled | 18.0013 | 18.4379 | 4,319,632 |
| 1080×720 | Baseline | 42.8784 | 45.9413 | 34,653,904 |
| 1080×720 | New, fresh | 42.2618 | 44.0699 | 24,258,320 |
| 1080×720 | New, pooled | 41.0097 | 42.9107 | 9,530,992 |

An independent rerun using the checked-in benchmark reproduces all six allocation medians exactly.
Its baseline/fresh/pooled p50 values were 19.8868/20.9011/18.8772 ms at 1080×300 and
45.2805/47.1228/47.4968 ms at 1080×720. Timing varies and the pooled case can be slower.

Approximately 72.5% fewer Java allocations after pool warmup, or 30% with fresh workspace creation.
This trades bounded retained scratch storage for less transient allocation. Median CPU changes
are small; no significant CPU speedup, Android FPS improvement or physical-device approval is claimed.
Full-resolution computation remains synchronous. An asynchronous or bounded-resolution policy is
separate future work and must preserve the geometry tests.

Reproduce from repository root (JDK 21+):

```sh
./gradlew -I tools/benchmarks/pathfield.init.gradle :liquidglass:jvmTest \
  --tests '*FieldAllocationBenchmarkTest' --rerun --max-workers=2
```

The benchmark sources are opt-in under `tools/benchmarks/pathfield`; they do not enter the normal
test suite or a published artifact.

## Cache geometry comparison

![Changed geometry rebuilds the cached field to match a fresh reference](field-cache.png)

Source: this change's `CacheFieldVisualTest`, candidate based on `4cd5ed8`, desktop JVM/Skia
software rasterization on the environment above. The test changes a notched path from 100×60
with pad 10 to 80×40 with pad 20; both use a 120×80 padded layer. Left is the actual first field
(the stale result the old key reused), middle is the corrected remembered field, right is a fresh
reference. Actual stored texels are enlarged 4× and annotated with Java2D; all middle/right pixels
are asserted equal before export. Visually inspected. This is a field diagnostic, not an app
screenshot, physical-device validation or frame-time evidence.

Reproduce with the same opt-in init script and `--tests '*CacheFieldVisualTest' --rerun`.
Output: `liquidglass/build/reports/atlas/compose-cache-field/field-cache.png`.

## Verification

- Before changes: lifecycle and geometry-cache reproductions fail (2 tests).
- Focused candidate: lifecycle, field-cache, buffer reuse/concurrency and existing field/odd-pad
  tests pass. Coverage includes shared consumers, superseded settings, portal/overlay disposal,
  abandoned leases, identical-input reuse, disabled capability, alternating sizes/shapes/padding,
  immutable prior outputs and concurrent builds.
- Paired allocation probe passes and checks identical baseline/new pixels at both measured sizes.
- Full renderer invocation: 423 tests, 393 passed, 27 opt-in showcase cases skipped, three
  `UncompletedCoroutinesError` timeouts. Four renderer workers, 768 MiB each, 17m38s.
  This first invocation failed; it is not reported as a clean full-suite pass.
- Unchanged-source rerun with one renderer worker: all three timed-out cases pass, along with
  `CacheFieldVisualTest` and `FieldAllocationBenchmarkTest` (5 tests, 4m). The retried cases are
  `GlassSurfaceDesktopTest.desktopPressIsPreservedWhileOnlyLargeSurfaceDragBecomesSubtle`,
  `GlassTabBarCalmGestureTest.aCancelAfterCrossingTabsSelectsNothingAndSettlesBack` and
  `GlassTabBarGestureTest.aDeclinedDragReturnsTheSelectorToTheSelectionTheHostHolds`.
  An isolated thread dump showed native Skia rendering during the surface test. This supports
  a timing/environment explanation but does not prove its root cause.
- `:sample:assembleDebug`, `:liquidglass:compileKotlinWasmJs` and
  `:liquidglass:checkKotlinAbi` pass. Strict MkDocs, repository links and whitespace checks pass.
- No physical-device run. Existing field/odd-pad render checks are the optical gates for this
  maintenance change; screenshots are not timing evidence.
