/// Driving a window from outside, for a run that has to prove something.
///
/// A frame loop's claims — a paragraph resized at 60 fps, say — cannot be
/// measured by a loop that nothing is moving. This package is what moves it
/// with no hand on the window: a [io.github.digitalsmile.goldberry.drive.ResizeWalk]
/// that changes the size a pixel a frame, which is what a drag produces, and
/// a [io.github.digitalsmile.goldberry.drive.FrameBudgetException] for a run that
/// missed more refreshes than it was allowed to (ADR-0342).
///
/// Nothing here is for an application. The launcher reads `--resize=WxH` and
/// `--late-budget=N` off the arguments it was handed, exactly as it reads
/// `--frames=N`, and everything else in the process ignores both.
@NullMarked
package io.github.digitalsmile.goldberry.drive;

import org.jspecify.annotations.NullMarked;
