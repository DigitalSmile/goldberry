/// GIF, decoded in Java: the first frame for a still picture, or every frame
/// composited under the file's disposal rules for an animation (ADR-0382).
///
/// A palette, LZW and a few block headers are small enough that owning the format
/// costs less than taking a second native dependency for it (ADR-0329), so it sits
/// beside `image.png` as the same kind of thing. Exported so an application with a
/// reason to reach the decoder directly — a thumbnail pipeline, a test fixture —
/// need not go through `image.Image`, which is where a malformed file surfaces as
/// the one decode exception every format shares.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.image.gif;

import org.jspecify.annotations.NullMarked;
