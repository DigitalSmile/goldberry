package dev.goldberry.media.picture;

import java.time.Duration;
import java.util.Optional;

import dev.goldberry.media.MediaPlayer;

/// Where a view's pictures come from: a [MediaPlayer] decoding a file, or an
/// application handing over pictures it made, such as a call's video arriving
/// over the network ([PushedPictures]).
///
/// A view asks [#shownPicture()] each time it draws. It learns when to draw
/// again from one of two signals, and a source gives whichever it has: a
/// player knows when its next picture falls due ([#untilNextPicture()]), and a
/// source that is pushed to knows the moment one arrives ([#onPicture]).
///
/// **The borrowing rule** is every source's: a picture handed out keeps its
/// bytes until two more pictures have been handed out after it. A view draws
/// the newest and has finished with the one before it by then.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
public interface PictureSource {

    /// The picture to show now, or empty when there is none yet.
    Optional<Picture> shownPicture();

    /// How long until the next picture falls due, when the source knows: a
    /// view waits that long and draws again. Empty for a source that does not
    /// know, which says so through [#onPicture] instead.
    default Optional<Duration> untilNextPicture() {
        return Optional.empty();
    }

    /// Tells `listener` each time a new picture is there to show, on whatever
    /// thread brought it, until the subscription is closed. A source that only
    /// knows when pictures fall due never calls it, which is this default.
    ///
    /// @return what removes the listener
    default AutoCloseable onPicture(Runnable listener) {
        return () -> {};
    }
}
