package dev.goldberry.media.picture;

import dev.goldberry.media.MediaPlayer;

/// A decoded picture, ready to draw, in one of the [PictureForm]s: converted to
/// BGRA ([VideoPicture]) or as its Y'CbCr planes ([VideoPlanes]).
///
/// What [MediaPlayer#shownPicture()] hands out. A view that draws either form
/// switches over the two:
///
/// ```java
/// switch (picture) {
///     case VideoPicture bgra -> blit(bgra);
///     case VideoPlanes planes -> upload(planes);
/// }
/// ```
///
/// Both are **borrowed** under one rule: the bytes live in a buffer the Engine
/// reuses, and a picture handed out keeps them until two more pictures have been
/// handed out after it.
public sealed interface Picture permits VideoPicture, VideoPlanes {

    /// Width in pixels.
    int width();

    /// Height in pixels.
    int height();

    /// When the picture is presented, in nanoseconds of stream time.
    long ptsNanos();
}
