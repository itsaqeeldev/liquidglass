# First use on a Pixel 7: 20 October 2026

What the first tab bar tap after a cold start costs, measured apart from forced redraws and screenshots
(TODO row A1). Pixel 7, Android 17, 90 Hz. The sample's `PhoneActivity`, release build (not minified,
signed with the debug key), source `origin/main` at `ab574ec` plus a library baseline profile.

**Method** ([measure.sh](measure.sh)). Each run force-stops the app, starts `PhoneActivity`, waits 3 s,
resets `dumpsys gfxinfo`, taps the Keypad tab with `adb shell input tap`, waits 1.5 s and reads
`framestats`; then taps Calls the same way. The figure is the slowest frame, IntendedVsync to
FrameCompleted. Before the runs: `ProfileInstallReceiver` broadcast, then
`cmd package compile -f -m speed-profile`, as a store install would.

| Build | run 1 (first ever) first tap | runs 2-5 first tap | runs 2-5 second tap |
| :-- | --: | --: | --: |
| without the library's baseline profile | **370 ms** | 39 / 48 / 45 / 39 ms | 35 / 33 / 37 / 29 ms |
| with it (`src/androidMain/baselineProfiles`) | **317 ms** | 65 / 58 / 31 / 36 ms | 41 / 34 / 33 / 32 ms |

**Reading.** The hitch is the first tap after installing: one frame of 317-370 ms. It does not come back on
later cold starts, which is the signature of GPU shader compilation that Android caches per install: the
selector's shaders are compiled on their first use. The baseline profile did not change the warm numbers
beyond their run-to-run spread (about ±15 ms) on this phone; it ships because it costs nothing and helps
devices that would otherwise interpret the library's code, which this measurement does not show.

**Open.** Compiling the selector's shaders before the first gesture (a warm-up draw with the same effect
chains) would move the one-time hitch out of the interaction; not done. *Superseded: the cause was the
selector's layers being reallocated mid-gesture, and the frame is gone without a warm-up; see
[the fix](../first-tap-fix-2026-10-20/README.md). The baseline profile was removed there.* One phone; not measured on lower-end
devices ([help wanted](https://github.com/shayann07/liquidglass/issues/65)). `adb` input is not a finger;
presented-frame timing on the display is not measured here.
