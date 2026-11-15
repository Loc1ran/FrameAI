# Viewpoint guidance

Subject and Scene AI now distinguish aiming the phone from changing the
photographer's position. No extra mode or selection step is required.

- Pan and tilt still draw framing arrows.
- Step, walk around, distance and height changes show a labeled body-movement
  instruction in Athena's card, with the photographic reason. They do not draw
  a dashed destination or a pan arrow, trigger digital zoom, or declare success
  merely because the subject is centered.
- `orbit_left` and `orbit_right` mean a small walk around the subject toward the
  photographer's own left or right, keeping the subject in view at a similar
  distance. No angle or distance is measured or prescribed.
- Viewpoint suggestions require the existing director confidence/reason checks
  in both Subject and Scene. During a viewpoint suggestion the next preview is
  reanalyzed even if the subject's position within the image has not changed.
  Requests still use the normal camera cadence and single-request limit.

The model considers visible overlap, subject shape, perspective, depth and the
requested photography style before minor framing adjustments. It must offer
one small, evidence-based experiment, preserve a working composition and avoid
claiming knowledge of unseen space. It assesses each new image independently;
this is not a 3D reconstruction, before/after comparison, or motion tracker.
It cannot know that the photographer actually followed the instruction.

Update app and server together: older clients do not understand the two orbit
actions. Action burst mode is unchanged. Test on a phone with a stationary
subject: pan in place, step sideways, walk a short arc while keeping the subject
centered, then lower the viewpoint. Confirm that physical cues have no target
arrow and that new views are analyzed. Model usefulness and live UI behavior
still need device testing; unit tests do not establish photographic quality.
