# Measured framing UI

## Camera-style rotation — November 15, 2026

The camera keeps a portrait coordinate system when the phone turns. Preview,
grid, subject markers, shutter, and control slots do not move or change aspect.
Only the labels, guidance text, and menu content turn in 90-degree steps. Their
content is measured in the reading direction so sideways text fits its slot.
The orientation listener pauses with the screen and ignores diagonal jitter.

The photo editor can still use its landscape layout. CameraX 1.3.4 already
updates capture orientation from its own sensor listener, independently of the
fixed preview. Tracking and composition code remain unchanged by this update.

Checks cover unchanged preview/shutter bounds at 0, 90, 180, and 270 degrees,
rotated label bounds, usable menus, aspect-ratio selection, orientation jitter,
and retaining an edit across editor rotation. Physical sensor calibration on
other phones has not been checked.

## Earlier responsive UI update

The camera now has one compact toolbar: subject/scene mode, photo shape, and an
options menu for the guide and AI. The default photo shape is 4:3. Explicitly
choosing Full still fills the available preview area. Fixed shapes fit inside
both available dimensions; they do not stretch when the phone turns.

Portrait keeps the preview above guidance and capture controls. The initial
landscape camera layout was replaced by the fixed layout above. Longer advice scrolls independently while
the shutter remains available. The editor also uses a side-by-side layout in
landscape and keeps the photo visible while scrolling its controls in portrait.

Orientation and size changes update the layout without recreating the activity,
so an in-progress edit is preserved. Existing tracking, subject selection, and
composition logic were left unchanged in this UI update.

Validation: Android build, 53 local tests, and 12 device tests passed on RMX3842.
The new device checks cover 4:3, 16:9, and square previews in both orientations,
visible capture controls, and rotating during an asynchronous look application.
Screenshots use a real camera preview and a synthetic editor fixture. Other
devices, extreme font sizes, and multi-window resizing have not been verified.

The notes below describe the earlier framing redesign; current composition
rules have since evolved independently.

The former UI generated a default subject rectangle when no detection was available,
could show READY from a generic scene rule, and mixed cloud suggestions with local
movement cues. A dashed target and short “Move” label did not explain whether to
move the subject, the phone, or the lens.

The revised camera uses an explicit framing exercise:

- The gold target is the center of the frame. There is no placement selector.
- A white outline and dot represent the measured subject. No detection means no
  invented subject, direction, or ready state.
- One instruction names the phone action and explains how the subject will move
  on screen. The larger position error is handled first.
- The gold target area uses the same tolerance as the alignment calculation.
  Position must remain aligned for 600 ms before the UI says “Position ready.”
- A clipped subject receives room/zoom advice without an invented physical distance.
- Measurements expire after 1.5 seconds; returning from background clears them.
- Zoom is an explicit control. Capture remains available even without a subject.
- Optional AI scene notes are separate, off initially, and disclose preview sharing.
  They cannot move the measured target or override position readiness.

The preview, explanation, and shutter now have separate areas. Review shows the
original first, applies a look on tap, labels the completed result, and saves/shares
the visible version. A full-size original is retained; edited export remains capped
at 1024 pixels on its long edge.

## Validation

- Android build and 39 local tests passed.
- Seven device tests passed on RMX3842 / Android API 36: camera analyzer delivery
  with a controlled detection; coordinate rotation; real packaged LUT pixels;
  gallery success and failed-write cleanup; apply locking; visible grading errors;
  sharing the unsaved edited pixels.
- Camera and review layouts were inspected on the phone. The review screenshot
  uses a synthetic image; it is not a real-photo quality benchmark.
- Whole-scene artistic judgment, physical pan/tilt calibration across other phones,
  large-font/small-screen combinations, and live cloud quality need further testing.

Changes were built on the current working tree, preserving its ongoing subject
tracking and server work. Prior UI sources were copied to the task's local
before-redesign backup directory before replacement.
