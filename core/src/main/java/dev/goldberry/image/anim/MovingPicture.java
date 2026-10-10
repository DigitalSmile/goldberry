package dev.goldberry.image.anim;

import dev.goldberry.paint.Frame;

/// A picture that moves: drawn at any moment of its time, into any rectangle.
///
/// What `AnimationView` in the widget catalogue plays on the frame loop. It
/// holds no clock, as [Animation] holds none: the caller says how long it has
/// been playing, and the picture draws that moment. Two kinds are drawn this
/// way. A [VectorAnimation] is a Lottie document drawn as vectors, and
/// `goldberry-media`'s `VideoAnimation` is a video sticker, VP9 with its
/// transparency, decoded a picture at a time.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public interface MovingPicture {

    /// The natural width, in logical pixels: the size it is drawn at when
    /// nothing says otherwise, and the shape every drawing of it keeps.
    double width();

    /// The natural height, in logical pixels.
    double height();

    /// Whether it has stopped moving `elapsedMillis` in: never for one that
    /// plays for ever, and from its last moment on for one that does not.
    boolean isDoneAt(long elapsedMillis);

    /// Draws the moment `elapsedMillis` in onto `frame`, stretched over the
    /// rectangle `(x, y, width, height)`, over what is already there.
    void paint(Frame frame, long elapsedMillis, double x, double y, double width, double height);
}
