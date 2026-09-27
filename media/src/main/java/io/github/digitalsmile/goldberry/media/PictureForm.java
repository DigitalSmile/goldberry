package io.github.digitalsmile.goldberry.media;

/// The form a player's pictures wait in, ready to draw (`docs/gpu-plan.md`, D8).
///
/// The video thread prepares each picture it keeps before the decoder is called
/// again, since a decoded frame is borrowed. What it prepares depends on who
/// draws it. A view draws on the CPU by blitting BGRA, and a view that uploads
/// planes to the GPU converts them in a shader. A view says which it draws with
/// [MediaPlayer#attachView(PictureForm)], and the player decides from every view
/// attached to it ([MediaPlayer#pictureForm()]).
public enum PictureForm {

    /// Premultiplied BGRA, converted by swscale on the video thread: a
    /// [VideoPicture]. What CPU present blits, and what a player with no view
    /// attached keeps.
    CONVERTED,

    /// The decoded Y'CbCr planes, copied as they are: a [VideoPlanes]. What GPU
    /// present uploads. A copy costs the video thread less than a conversion.
    /// A view that asks for planes must also draw a [VideoPicture]: the queue
    /// may still hold converted pictures when the form changes, and a view that
    /// draws on the CPU, attached to the same player, keeps the form
    /// [#CONVERTED].
    PLANES
}
