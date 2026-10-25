package dev.goldberry.example.media;

import dev.goldberry.media.MediaPlayer;
import dev.goldberry.widgets.markup.Named;

/// The Audio and Video screens' players, and the decoder the Audio screen wrote
/// in Java with its switch.
///
/// Made with the application and not opened: nothing loads the media natives or
/// opens an audio device until a source is picked. Not a model either — none of
/// these is a value that changes, so the documents reach the players as named
/// objects.
///
/// @param javaPcmDecoder the decoder the Audio screen wrote in Java
/// @param audioPlayer    the Audio screen's player, which takes that decoder
/// @param videoPlayer    the Video screen's player, a second one: the two
///                       screens play at the same time if both are started, as
///                       two players in one application would
public record ShowcasePlayers(JavaPcmDecoder javaPcmDecoder, MediaPlayer audioPlayer, MediaPlayer videoPlayer)
        implements AutoCloseable {

    /// The showcase's players, over its own protocols — see [ShowcaseMedia].
    public static ShowcasePlayers create() {
        var javaPcm = new JavaPcmDecoder();
        return new ShowcasePlayers(javaPcm, ShowcaseMedia.audioPlayer(javaPcm), ShowcaseMedia.videoPlayer());
    }

    /// Registers the players `audio.kdl` and `video.kdl` name on `named`, and
    /// answers it. Each screen's cards drive the same player from Java at the
    /// same time.
    public Named register(Named named) {
        return named.bind("audio.player", audioPlayer).bind("video.player", videoPlayer);
    }

    /// Stops the engines' threads and closes the audio devices, if a source was
    /// ever opened.
    @Override
    public void close() {
        audioPlayer.close();
        videoPlayer.close();
    }
}
