/// Driving a window from outside, for a run that has to prove something.
///
/// A frame loop's claims — a paragraph resized at 60 fps, say — cannot be
/// measured by a loop that nothing is moving. This package is what moves it
/// with no hand on the window: a [dev.goldberry.drive.ResizeWalk]
/// that changes the size a pixel a frame, which is what a drag produces, and
/// a [dev.goldberry.drive.FrameBudgetException] for a run that
/// missed more refreshes than it was allowed to.
///
/// Nothing here is for an application, and the package is not exported. The
/// launcher reads `--resize=WxH` and `--late-budget=N` off the arguments it was
/// handed, exactly as it reads `--frames=N`, and everything else in the process
/// ignores both.
///
/// Read more: [Testing an application](https://goldberry.dev/docs/guide/testing.html#driving-input).
@NullMarked
package dev.goldberry.drive;

import org.jspecify.annotations.NullMarked;
