# FrameAI: UI, guidance, and LUT repair plan

Reviewed November 14, 2026. [GitHub task tracker](https://github.com/Loc1ran/FrameAI/issues/3).

## Start from the same build

GitHub `main` at `2b135f2` still constructs `FallbackPhotoGrader` and supplies no face detector or composition guide. The integrated implementation reviewed here is `feat/vision-lut` at `203224b`, in [PR #1](https://github.com/Loc1ran/FrameAI/pull/1). Do A0 before implementing repairs. The installed phone build has not been matched to either commit.

This is a work breakdown, not a claim that repairs are implemented or that PR #1 has been approved. Roles follow the PDF: A owns the Android experience and integration; B owns guidance, LUTs, and their tests. No GitHub assignees are invented.

## What the audit found

The [18-page PDF plan](https://github.com/Loc1ran/FrameAI/blob/main/docs/FrameAI_24h_GoogleCloud_Two_Developer_Full_Plan.pdf) describes an offline portrait exercise: frame a subject, capture, choose one of two looks, compare the same image, save, and optionally receive advice. Pages 6–8 define UI, guidance, and LUT integration. Pages 12–13 define contracts and acceptance checks. It does not require a large preset browser or live GPU grading. Full-resolution export and strength controls are optional improvements.

1. **No visible LUT effect has two concrete code paths.** Selecting a chip clears the edit and displays Original; it does not apply the LUT. Separately, `CubePhotoGrader` catches runtime errors and returns the source image as success, which ReviewScreen labels After. These mechanisms are confirmed from source; the user's exact phone failure has not been reproduced.
2. **Guidance can stall on a thread violation.** Camera analysis runs on a worker and reads `PreviewView.outputTransform`. The installed CameraX 1.3.4 source explicitly calls `checkMainThread()` there. The broad catch suppresses the error and skips delivery of a new guide when that path is reached.
3. **Selection and rendered result can disagree.** LUT chips stay enabled during asynchronous grading and saving. The selected id is mutable and the UI labels the result using the current selection.
4. **Export and share do not consistently represent the displayed edit.** Share uses the original until an edit is saved. A null gallery-export result is ignored, and API 26–28 always returns null. Saved edits use the at-most-1024px graded preview; the original is kept, but this resolution limit is not disclosed.
5. **The instructional UI does not explain the exercise well.** Marker and ring meanings are unexplained; a fixed-size ring does not match the engine's normalized readiness tolerances; existing smoothing is unused. Review has duplicate Back/Retake actions and lacks explicit system-bar padding. These layout concerns were identified in code, not a rendered-device UI audit.
6. **Offline teaching is disconnected.** The UI uses one static JSON tip instead of the existing `OfflineCoach.review(guide)`; capture does not pass its guide context to review.
7. **Catalog claims exceed the actual UI.** The package contains 114 looks excluding identity, while only Warm Film and Cool Cinema are presented. Some hidden assets are camera-log conversions. Keep the two verified phone-photo looks for this repair.

## Evidence and limits

Fresh checks on the integrated working tree:

- 23 brain tests passed: 7 guidance, 9 LUT, 1 offline coach, 6 coordinate-math tests.
- 4 Android contract tests passed with the project's JDK 21 configuration.
- The brain suite excludes CameraX/UI/Android bitmap wrappers; its coordinate tests do not execute the production `AnalysisCoordinateMapper`.
- The actual Warm Film/Cool Cinema data produces different colors at the UI's 85% strength. For neutral RGB (128,128,128), an independent interpolation calculation gives Warm (150,141,137) and Cool (126,134,167). This checks the assets, not the phone UI.
- No three-run physical-device acceptance test, saved/share output inspection, or live cloud request was performed during this audit.
- Existing modified packaged contract files were not included in the planning commit.

## Small jobs

Estimates are active implementation time, not delivery commitments. Each issue contains the exact files, evidence, scope, and acceptance checks.

| Job | Owner | Deliverable | Priority | Estimate | Depends on |
|---|---|---|---|---|---|
| [A0](https://github.com/Loc1ran/FrameAI/issues/4) | Developer A | Establish one integrated build for both developers | P0 | 15–30 min | — |
| [B1](https://github.com/Loc1ran/FrameAI/issues/5) | Developer B | Stop reporting a failed LUT as a successful edit | P0 | 20–40 min | A0 |
| [A1](https://github.com/Loc1ran/FrameAI/issues/6) | Developer A | Fix the thread violation that stalls face guidance | P0 | 30–60 min | A0 |
| [A2](https://github.com/Loc1ran/FrameAI/issues/7) | Developer A | Make choosing a look visibly apply the correct result | P0 | 30–60 min | A0, B1 |
| [B2](https://github.com/Loc1ran/FrameAI/issues/8) | Developer B | Verify the two phone-photo LUTs and reconcile the catalog | P1 | 30–45 min | A0, B1 |
| [B3](https://github.com/Loc1ran/FrameAI/issues/9) | Developer B | Define clear and stable portrait guidance states | P1 | 20–40 min | A0 |
| [A3](https://github.com/Loc1ran/FrameAI/issues/10) | Developer A | Make Save and Share match the photo being shown | P0 | 30–60 min | A2 |
| [A4](https://github.com/Loc1ran/FrameAI/issues/11) | Developer A | Clarify the camera and review controls | P1 | 30–45 min | A1, A2, B3 |
| [A5](https://github.com/Loc1ran/FrameAI/issues/12) | Developer A | Connect offline advice to the captured framing state | P1 | 20–40 min | A0, A1 |
| [Q1](https://github.com/Loc1ran/FrameAI/issues/13) | A + B / optional C | Run and record three complete offline phone demos | P0 release gate | 20–30 min | A0, A1, A2, A3, A4, A5, B1, B2, B3 |

## Work order

1. **A0:** A establishes the shared integrated baseline; B verifies the module handoff.
2. **Parallel first repairs:** A1 fixes camera guidance; B1 fixes LUT error reporting.
3. **Parallel LUT verification:** A2 fixes selection/apply state after B1; B2 verifies packaged looks.
4. **Parallel completion:** A3 fixes save/share while B3 validates guidance behavior and hands off its geometry/copy.
5. **A finishes UI wiring:** A4 integrates B3 and clarifies controls; A5 connects capture-context offline advice. A2/A3/A4 all touch ReviewScreen, so merge them sequentially.
6. **Q1:** A and B complete three real-phone demos. Optional C can collect evidence and run the checklist.

If time is tight, finish A0/A1/B1/A2/A3 and validate that narrower build immediately. Remaining P1 work stays visibly open; do not claim the complete improvement plan is finished.

## Ownership and handoffs

- **A:** `MainActivity`, `camera/`, `ui/`, Android integration tests, install/release checks.
- **B:** `guide/`, `lut/`, LUT assets, pure logic tests. B1 preserves the PhotoGrader signature; A2 consumes its success/error behavior.
- **Shared contracts:** preserve field names and enum values. Coordinate changes to message fixtures; root and packaged copies must match.
- **B3 → A4:** hand over existing smoother usage, message text and readiness bounds; B does not simultaneously edit A's screens.
- **A3 export limit:** keep the tested 1024px MVP output with honest labeling unless a separately reviewed full-resolution path is justified.
- **A5:** reuse the existing OfflineCoach, with capture-time guide context; it must not pretend to analyze image content.
- Each job should be a small PR closing its issue. A integrates in dependency order. Do not add general refactors or unrelated server work.

## Completion gate

A user unfamiliar with the app must be able to open it, understand the target, capture a photo, tap a look and see a change, compare Original/Edited, and save/share the intended result without verbal coaching. Failures must stay visible and must never be presented as successful edits. The original must remain intact.

Skipped scope: new cloud deployment, all-114-look picker, live video/GPU grading, accounts, new dependencies, and optional full-resolution export. Known risk: tests passing does not prove the phone pipeline; Q1 remains required.

