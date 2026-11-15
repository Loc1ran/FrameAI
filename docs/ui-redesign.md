# Measured framing UI

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
