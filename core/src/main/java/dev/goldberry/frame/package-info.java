/// The steps a frame runs, shared by everything that runs them.
///
/// One type, [FrameSequence][dev.goldberry.frame.FrameSequence],
/// which holds the list of steps a frame runs before it is painted, in the order
/// that is right. A window runs them and an offscreen render runs them, from the
/// one copy, so neither can skip a step the other has.
///
/// **Its own package, and not exported.** It names the element tree, the cascade,
/// the render tree, the paint pipeline and the hit test, so it cannot live inside
/// any one of them without pointing that package at the other four. It is a seam
/// between two callers in this module rather than a promise to an application;
/// what an application wants is
/// [Offscreen][dev.goldberry.offscreen.Offscreen].
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [What a frame costs](https://goldberry.dev/docs/performance/index.html#the-frame-loop).
@NullMarked
package dev.goldberry.frame;

import org.jspecify.annotations.NullMarked;
