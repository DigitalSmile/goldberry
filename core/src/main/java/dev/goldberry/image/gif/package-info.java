/// GIF, decoded in Java: the first frame for a still picture, or every frame
/// composited under the file's disposal rules for an animation.
///
/// A palette, LZW and a few block headers are small enough that owning the format
/// costs less than taking a second native dependency for it, so it sits beside
/// `image.png` as the same kind of thing. Exported so an application with a
/// reason to reach the decoder directly — a thumbnail pipeline, a test fixture —
/// need not go through `image.Image`, which is where a malformed file surfaces as
/// the one decode exception every format shares.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
@NullMarked
package dev.goldberry.image.gif;

import org.jspecify.annotations.NullMarked;
