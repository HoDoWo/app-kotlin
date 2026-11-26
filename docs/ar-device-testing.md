# AR guide: device test matrix and QA checklist (#241)

How the AR guide was tested on real hardware, how to repeat the test, and what it
cannot do yet. Fill the tables on the devices you test and paste them in the PR.

## How the AR guide works

1. In a repair guide, **View in AR** (only on ARCore-supported phones) opens the camera on the same step.
2. The user aims the dot at the control of the first pinned step and taps **Place point**,
   then at the control of a second step far from it in the photo (**2 of 2**).
3. From these two points the app fits the guide photo to the appliance (position, size and turn)
   and puts every numbered pin on it. The current step gets the arrow.
4. **Previous / Next** walk through all steps in AR; **Re-align** places the two points again;
   **Open 2D guide** goes back to the photo on the same step.

**Find automatically** (when the backend is configured) does steps 2–3 for the user: the app sends
one camera frame, the guide steps and the guide photo to `POST /api/guide/locate`; the AI says where
the two controls are in that frame. Because the answer takes a few seconds and the phone moves, the app
does not hit-test the screen: it casts a ray from the camera *as it was* when the frame was taken and
meets the planes ARCore tracks now. If only the first control is found, the user aims at the second;
if none, both. The manual **Place point** is always next to it.

All pins are children of the first anchor: they move with it and do not drift apart.
If all pins are on one control, one point is enough; a guide without pins shows a free arrow.

## AR tab: scan without chat or guide

**AR tab → Start AR**, point at an appliance, a keyboard, a remote… As soon as ARCore finds a surface the
app sends one frame to `POST /api/ar/scan`: the AI names the device and up to 8 main controls, and the app
anchors a small dot and the control's name on each one (same ray-to-plane method as auto-alignment).
Each dot is placed from the best source available: a tracked plane the ray meets (hobs,
desks), else **ARCore depth** at that point (vertical, glossy or plain fronts where ARCore finds no
plane: ovens, microwaves, washing machines; on phones that support depth), else the **feature points**
ARCore tracks along the ray, else a plane just outside its area, else along the ray at the distance of
the other controls (50 cm when none was measured). The scan photo waits up to 1.5 s for depth.
A dot at the wrong distance looks right from where the photo was taken but slides off when the phone
moves, so check from another angle. Logcat tag `ArScan` says which source placed each dot
(`captured: depth=… features=…`, then `placed by plane | depth | features | plane edge | ray`).
The labels are drawn flat on the screen above each dot (sharp, same size at any distance) and
can be tapped. On the device each control shows only its **number**; the panel lists the numbered controls with what
they do (readable with TalkBack), and tapping one shows its name on the device (the first one is
selected). The panel has a handle: tap it or drag it down to lower the panel and see the whole camera
view, up to raise it again. While the AR camera is open the tab bar and "New chat" are hidden. **Scan again**
repeats it; there is no continuous scanning (each scan is one AI call).

## How to measure performance

Debug builds show a line in the top-right corner of the AR view:

```
16.9 ms avg · 34 ms worst · 59 fps · thermal none
```

It is also written to Logcat every 5 seconds:

```bash
adb logcat -s ArPerf
```

Test protocol: align a guide, then keep the phone pointed at the appliance for **5 minutes**,
moving slowly between the pins. Write down the values at 0, 1 and 5 minutes.
Thermal status comes from Android (`PowerManager`, Android 10+): none, light, moderate, severe…

Targets: average under 33 ms (30 fps or better), no thermal status above *moderate* in 5 minutes.

## Device matrix

| Device | Type | Android | ARCore | Plane found on appliance front | Alignment (2 points) | Pins on the right controls | Drift after 1 min | Avg / worst frame (0 → 5 min) | Thermal at 5 min | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| _e.g. Pixel 7a_ | Phone (mid-range) | 15 | 1.5x | Yes, ~4 s | OK | 5/6 | None | 17/30 → 18/34 ms | light | |
| | Phone | | | | | | | | | |
| | Tablet | | | | | | | | | |
| Pixel emulator (Apple Silicon) | Emulator | | Not supported | — | — | — | — | — | — | Shows "AR isn't available"; 2D guide works |

The issue asks for at least **one ARCore-supported phone and one tablet**. Check the device on
<https://developers.google.com/ar/devices> before testing.

## Manual QA checklist

Entry and fallback
- [ ] Phone without ARCore: no **View in AR** in the guide; AR tab says AR isn't available; 2D guide works.
- [ ] Phone with ARCore: **View in AR** opens on the step selected in the 2D guide.
- [ ] **Open 2D guide** returns to the photo on the step shown in AR.

Permission and session
- [ ] First open: camera permission asked; denying shows **Allow camera** and **Use the 2D guide**.
- [ ] "Don't ask again" → **Open Settings**; granting there and coming back starts the camera.
- [ ] Home button and back: the session resumes, pins still in place.

Alignment and overlay
- [ ] The status line names the control to aim at (step number and title), for point 1 and point 2.
- [ ] Aiming at two wrong points (e.g. far apart on the wall) shows "Those two points don't match the photo".
- [ ] After alignment every pinned step shows its number; steps on one control share a label ("2, 5").
- [ ] The current step has the arrow and the blue label; **Next** moves both.
- [ ] A step without a pin says "has no point on the appliance".
- [ ] Walking around the appliance, pins stay on their controls (no visible drift).
- [ ] **Re-align** removes the pins and asks for the two points again.

Auto-alignment
- [ ] **Find automatically** shows "Looking for steps X and Y…" and then the pins, without aiming.
- [ ] With the appliance out of view: "The AI couldn't find the controls", manual aiming works.
- [ ] Airplane mode: "Automatic search didn't work (check the connection)", manual aiming works.
- [ ] Moving the phone while it searches: the pins still land on the right controls.
- [ ] Time from the tap to the pins: ______ s (write it in the device matrix notes).

AR tab scan
- [ ] Dots show numbers only; tapping a control in the list shows its name on the device, tapping it again hides it.
- [ ] Lower and raise the panel with the handle (tap and drag); TalkBack reads "Hide details" / "Show details".
- [ ] No "New chat" button or tab bar over the AR camera; no arrow is placed by tapping the camera.
- [ ] Phone in Italian: every AR text is in Italian, and the scan names and descriptions too.
- [ ] Start AR on a keyboard: within a few seconds the device name and labels (Enter, Space, Esc…) appear on the keys.
- [ ] Same on an appliance panel (Start, programme dial, temperature…).
- [ ] Pointing at a wall: "No appliance or keyboard found", **Scan again** works.
- [ ] Airplane mode: "The scan didn't work (check the connection)".
- [ ] **Reset AR** removes the labels and scans again by itself.
- [ ] Oven or microwave front: walk half a metre to the side; the dots stay on the knobs/buttons.
- [ ] Tapping a number on the camera selects it (pill with its name), like tapping its row.

Guide on an upright front (oven, microwave)

ARCore rarely finds a plane on appliance fronts. Guide pins can now lie on a surface measured by
depth or feature points: **Place point** turns on as soon as the reticle is on the front, and **Find
automatically** places the pins from depth/feature points, taking the front as upright and facing the
camera (flat when looking down at it). Logcat `ArScan`: `guide depth`, `guide features`, `guide ray`.

- [ ] Open a guide → **View in AR** on an oven front: move the phone a little, **Place point** turns on.
- [ ] **Find automatically** on the same front places the pins on the knobs.

Navigation
- [ ] **Previous / Next** reach every step; disabled at the first / last step.

Readability and accessibility
- [ ] Outdoors or next to a window: status line, step card and pin numbers are readable.
- [ ] Largest font size (Settings → Display → Font size): the panel scrolls and does not cover the whole camera.
- [ ] TalkBack: every state change is announced; all buttons are reachable and labelled.

Tracking loss
- [ ] Covering the camera / moving fast shows the reason and recovers.
- [ ] **Reset AR** starts a new session keeping the current step.

## Languages

All AR texts are string resources (`res/values/strings_ar.xml`, Italian in `values-it/`); the ViewModel
gives keys (`ArText`) that the UI translates. The scan sends the app language at the time of the scan,
so it follows an in-app language switch. A new language = one more `values-xx/strings_ar.xml`.

## Known limitations

- **Glossy, plain fronts** (white or stainless steel) are slow to give ARCore a plane. Pins need a real plane,
  so the user may have to include edges, labels or the handle in view for a few seconds.
- **The photo must be taken roughly from the front.** The fit uses position, size and turn, not perspective:
  a photo taken at a strong angle puts the pins farther from the controls the farther they are from the two aimed points.
- **Pins are as good as the AI's pins on the photo**: a wrong pin in the 2D guide is wrong in AR too.
- **Curved or stepped panels** (a dial standing out, a curved door) are treated as one flat surface.
- **The two aimed controls must be visible in the photo** and far enough apart (at least ~12 % of the photo width).
- AR does not work in the Android emulator on Apple Silicon (arm64): use a real phone.
- Frame time and thermal values are shown only in debug builds.
- **Find automatically** needs the network, takes a few seconds and costs one AI call per try. It
  sees ARCore's camera image (usually 640 × 480), so very small labels may not be read.
