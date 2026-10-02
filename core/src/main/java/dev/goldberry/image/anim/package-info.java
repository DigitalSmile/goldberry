/// A picture with more than one frame in it.
///
/// [Animation] is the value — frames, delays and a loop count — and nothing
/// here holds a clock: what to draw is a function of how long the caller says
/// it has been playing, which is the same division every transition in the
/// toolkit is built on. Exported to applications; `Image.decodeAnimation`
/// is the usual way to get one.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
@org.jspecify.annotations.NullMarked
package dev.goldberry.image.anim;
