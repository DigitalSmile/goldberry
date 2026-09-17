/// A picture with more than one frame in it.
///
/// [Animation] is the value — frames, delays and a loop count — and nothing
/// here holds a clock: what to draw is a function of how long the caller says
/// it has been playing, which is the same division every transition in the
/// toolkit is built on ([ADR-0382]).
@org.jspecify.annotations.NullMarked
package io.github.digitalsmile.goldberry.image.anim;
