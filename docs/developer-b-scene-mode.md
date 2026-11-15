# Developer B: Subject / Scene composition

Branch: `feat/b-scene-composition`. Subject retains A's existing framing flow.
Scene asks Gemini to interpret the full image: people, tools, action direction,
meaningful surroundings, and negative space. It does not center a detected face
or object by default. For golf, the composition may include golfer, club, visible
ball and space toward the range. This is an example, not a fixed golf rule.

## Integration

The camera's existing AI toggle opts into preview sharing. Scene requests use
`POST /v1/live-coach?composition=scene`; Subject and old clients default to
`composition=subject`. The JPEG, token, and response contract are unchanged.
The server needs this branch's mode routing and scene prompt. Scene responses
must include `X-FrameAI-Composition: scene`; the client rejects an old server
that silently ignores the query parameter.

Scene displays the model's subject description and short instruction. It does
not draw the single-object tracker box as if it represented the whole activity.
The horizontal inset lines are general edge reminders, not AI-detected bounds.
Responses from a previously selected mode are discarded. Fresh reply timestamps
refresh expiry even for identical replies. Pausing clears advice and invalidates
pending responses. No response or an unavailable server never produces a green
ready check; mock replies are explicitly labeled and never marked ready.

The prompt protects visible feet, hands and equipment before removing foreground.
It distinguishes raising the phone from tilting it, and allows stepping back when
both ends would otherwise be clipped. It does not assume a higher camera is always
better or claim that one image measures height/degrees. No automatic lens movement.

## Validation and limits

JVM tests exercise scene presentation, mock/offline states and the absence of
fabricated readiness. Server tests exercise mode routing, validation and mock
labeling. These are integration safeguards, not evidence of model visual accuracy.

C must test the real Gemini server and phone: golf/action scene; empty landscape;
centered symmetric scene; clipped feet and clipped head; mode switch during a
request; server outage. Confirm shoes/club/ball are not sacrificed to a prettier
background and the advice remains practical. Test crowded scenes and uncertain
objects. A prompt does not guarantee correct object recognition or aesthetics.
The supplied reference photos stay outside the repository and were not sent to
Gemini. There is no Google credential on this laptop.
