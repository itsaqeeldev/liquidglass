# The first tap after install, fixed (#71) — 20 October 2026

[The first-use measurement](../first-use-2026-10-20/README.md) found one 317-370 ms frame on the first
tab bar tap after installing, on a Pixel 7, and attributed it to GPU shader compilation on first use.
This record finds which compilation, removes its cause, and measures the result the same way.

## What the slow frame was

`atrace` of the first tap with Skia's own tracing on (`setprop debug.hwui.skia_atrace_enabled true`;
`atrace --async_start -a com.wexpa.liquidglass.sample gfx view`), parsed per RenderThread slice:

| In the 303 ms frame | ms |
| :-- | --: |
| `flush layers` (offscreen layers with their render effects) | 301.4 |
| `FillRectOp` → `CreateGraphicsPipeline-CompileAfterCacheMiss`, first pipeline | **286.4** |
| the same, second pipeline | 3.1 |

One large Vulkan pipeline was compiled in the middle of the tap. By its cost it is the glass panel shader
(the 51 KB `GlassShaderSource`; the largest compile of the cold launch takes 352 ms), but the trace does
not name programs. Uniform values never make a
new pipeline, so something structural changed. The layer list of each frame shows what: the selector
node's four layers are 756 × 505 px at rest, **748 × 497 in the slow frame, 740 × 489 in the next**, then
756 × 505 again.

The selector's record pad follows its body. `recordPad` takes `0.3 ×` the body's optical band for the
held profile and folded caps, and the band of the pose body narrows as it deforms under the press. Every
new pad reallocated the node's layers, and the first frame drawn at a new size needed a pipeline that
had not been built for that layer before.

**Check.** The same build with the pad pinned through the existing diagnostic
(`LiquidGlassDiagnostics.recordPadOverridePx`, a throwaway intent extra, not committed): first-tap
slowest frame 90 / 97 / 99 ms at 40 px and 72 / 102 / 83 ms at 60 px, against 326-352 ms unpinned.

## The fix

`GlassPadFloor` holds the largest pad a selector body has needed (per node and render scale). The
recording, the shader's `uPad` and the draw-back all read the held value, so the
coordinate contract is unchanged; a larger pad only records more backdrop margin. Panels without a
body keep their pad exactly as before.

## Measurements

Pixel 7, Android 17, 90 Hz, the sample's `PhoneActivity` release build (signed with the debug key).
Before each build's runs: `ProfileInstallReceiver` broadcast and `cmd package compile -f -m speed-profile`.
[`measure.sh`](../first-use-2026-10-20/measure.sh) with `CLEAR=1` (new: `pm clear` before every run, so
every first tap is a first-install tap; the shader cache lives in the app's data), Keypad tab at
(676, 2200), then Calls at (220, 2200). Slowest frame, IntendedVsync to FrameCompleted.

| Build | first tap, slowest frame | second tap, slowest frame |
| :-- | :-- | :-- |
| before (`origin/main` at `4cd5ed8`) | 335 / 349 / 326 / 335 / 352 ms | 42 / 45 / 36 / 46 / 45 ms |
| the fix, with the library profile | 91 / 67 / 85 / 81 / 88 · 108 / 67 / 61 · 84 / 106 / 75 ms | 25 / 63 / 43 / 28 / 22 · 23 / 25 / 29 · 21 / 25 / 41 ms |
| **the fix without it (this change)** | **76 / 74 / 83 · 91 / 67 / 75 ms** | 38 / 34 / 26 · 35 / 39 / 30 ms |

The "·" separate series: the two profile builds were run alternately (without, with, with, without),
three runs per block.

**The trace after the fix** (the same `atrace` capture, this change's build, first tap after `pm clear`):
the selector node's four layers stay 756 × 505 px on every frame of the tap, and no large pipeline is
compiled. The slowest frame of the tap is 58 ms, with three smaller compiles (20, 20 and 5 ms); the rest
are 7-22 ms compiles spread over the following frames, not identified individually. The cold launch still
compiles the glass shaders (335 and 97 ms in its first frame), before any gesture, as it did before.

**Cold start** (`am start -W` TotalTime, force-stopped, caches warm), the before build and this change
alternated in blocks of five:

| Build | TotalTime | median |
| :-- | :-- | --: |
| before | 136 135 127 135 136 · 140 130 139 155 133 ms | 135.5 ms |
| this change | 128 137 139 144 130 · 129 138 133 145 135 ms | 136 ms |

An earlier series the same evening read 153-178 ms for both builds; the phone's state differed between
series, and within each series the builds overlap. No change is attributable to the fix, which adds one
small object per glass node and a comparison per draw.

## The library baseline profile is removed

The owner asked to narrow `liquidglass/src/androidMain/baselineProfiles/baseline-prof.txt` to the
measured path, or remove it if it still showed nothing. Measured on the fixed build, with and without it
(the app keeps Compose's own profile either way; the library's adds 429 bytes to `baseline.prof`), in
the alternating series above:

| Fixed build | first tap, slowest frame | second tap | cold start (warm caches) |
| :-- | :-- | :-- | :-- |
| with the library profile | 108 / 67 / 61 · 84 / 106 / 75 ms | 23 / 25 / 29 · 21 / 25 / 41 ms | 134 134 139 136 133 · 138 133 135 136 133 ms |
| without it | 76 / 74 / 83 · 91 / 67 / 75 ms | 38 / 34 / 26 · 35 / 39 / 30 ms | 132 128 138 133 129 · 138 135 135 140 139 ms |

The differences are inside the run-to-run spread, as they were before the fix, so the file is removed.

## Checks

- `./gradlew :liquidglass:jvmTest --rerun` with this change: 415 tests, 388 passed, 27 opt-in skips, no
  failures or errors (Windows, JBR 21, `--max-workers=4`, 26 min 55 s). It includes
  `GlassPadContractTest.aDeformingBodyKeepsOneLayerSize`: a held body's band moves the pad, the held pad
  never shrinks, always covers what the body needs, stays whole layer pixels at render scales 1, 0.75, 0.5
  and 0.25, and changes once rather than per frame.
- `checkKotlinAbi` passes: only internal declarations changed.

## Review follow-up (24 October): the fuse margin is not held

Review of PR #86 found that the first version held the *whole* record pad, fuse included. A held lens
that fuses with the tab bar's anchored trailing button needs the neck's margin (the gap to the button,
plus smoothing, plus the band), and that pad was then kept for the node's lifetime: in a desktop probe
the rest pad was ~36 px, the fused pad 143-173 px, and the pad stayed at 173 px after the fuse let go.
`GlassPadFloor.hold(body, fused)` now holds only the body's pad and adds the fuse's margin unheld, so
the layer returns to the body's size when the fuse lets go. `GlassPadContractTest.aFuseMarginIsNeverHeld`
pins it at render scales 1, 0.75, 0.5 and 0.25.

The measured tap path is unchanged by this: with no fuse, `body == fused` and the pad is exactly what
the measured build recorded. The numbers above were measured on the PR's build before this follow-up
(after `4cd5ed8`, before `3de39f6`; `3de39f6` only exempts the opt-in diagnostic pad override from the floor and `20cebf8` adds a comment, so neither changes what a normal app draws). This
follow-up was not re-measured on the phone, which was not connected; the long-press-and-fuse path was
never in the timing series.

**Raw captures.** The atrace and gfxinfo output behind these tables was parsed on the contributor's
machine and is not in the repository; only the parsed tables are. A re-run with
[`measure.sh`](../first-use-2026-10-20/measure.sh) (`CLEAR=1`) reproduces the table format.

## Limits

- One phone. `adb shell input tap` is not a finger; presented-frame timing on the display is not
  measured. `pm clear` reproduces the first-install shader cache; a real install also has no profile
  compiled until the store or `ProfileInstaller` does it.
- Desktop and web compile the same shaders on first use (the web demo's opening frames, #78). This change
  removes the reallocation everywhere, but it was only measured on Android.
- The before and after first-tap series ran on different builds installed minutes apart on the same
  phone, not interleaved run by run; the profile comparison and the cold start were alternated in blocks.
