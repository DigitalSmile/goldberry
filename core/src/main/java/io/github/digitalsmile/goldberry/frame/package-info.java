/// The steps a frame runs, shared by everything that runs them.
///
/// One type, [FrameSequence][io.github.digitalsmile.goldberry.frame.FrameSequence],
/// and it exists because there were two copies of a list of steps whose order is
/// the load-bearing part (ADR-0423). A window runs them, an offscreen render runs
/// them, and the copy that was missing one of them committed nine wrong pictures
/// (ADR-0284).
///
/// **Its own package, and not exported.** It names the element tree, the cascade,
/// the render tree, the paint pipeline and the hit test, so it cannot live inside
/// any one of them without pointing that package at the other four. It is a seam
/// between two callers in this module rather than a promise to an application —
/// what an application wants is
/// [Offscreen][io.github.digitalsmile.goldberry.offscreen.Offscreen].
///
/// `@NullMarked`, which puts this package under NullAway.
@NullMarked
package io.github.digitalsmile.goldberry.frame;

import org.jspecify.annotations.NullMarked;
