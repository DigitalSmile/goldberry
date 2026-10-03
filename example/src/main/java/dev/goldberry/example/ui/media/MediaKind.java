package dev.goldberry.example.ui.media;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.media.ShowcaseMedia;

/// Which of the two media screens: what it is called, what its header says, and
/// what it offers to play.
///
/// The two screens show one engine from two sides, and most of their cards are the
/// same card over a different player, so the differences are here rather than in
/// two copies of the cards.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
public enum MediaKind {
    /// The Audio screen: `audio-player`, `media-controls`, and a decoder written in
    /// Java.
    AUDIO(
            "audio",
            "Audio",
            "audio-player is compact controls over a MediaPlayer the application owns: play, the times, a seek bar,"
                    + " mute and volume. The cards drive the same player from Java and show what it reports.",
            "Audio"),
    /// The Video screen: `media-player`, `video-view`, subtitles and hardware
    /// decoding.
    VIDEO(
            "video",
            "Video",
            "media-player is the picture with its controls laid over it, driving a MediaPlayer the application owns."
                    + " Pick a source; the cards drive the same player from Java and show what it reports.",
            "Video");

    private final String id;
    private final String title;
    private final String summary;
    private final String filterName;

    MediaKind(String id, String title, String summary, String filterName) {
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.filterName = filterName;
    }

    /// The screen's name in the gallery, and the prefix of every id on it.
    public String id() {
        return id;
    }

    /// `name`, prefixed with this screen's: `audio-sources`, `video-sources`.
    public String id(String name) {
        return id + "-" + name;
    }

    /// The screen's heading.
    public String title() {
        return title;
    }

    /// What the screen's header says it is for.
    public String summary() {
        return summary;
    }

    /// The section the screen's header links.
    public DocLink doc() {
        return switch (this) {
            case AUDIO -> MediaDocs.AUDIO_PLAYER;
            case VIDEO -> MediaDocs.MEDIA_PLAYER;
        };
    }

    /// What the file dialog calls the files it offers.
    public String filterName() {
        return filterName;
    }

    /// The bundled sources the picker lists.
    public List<ShowcaseMedia.Sample> samples() {
        return switch (this) {
            case AUDIO -> ShowcaseMedia.AUDIO_SAMPLES;
            case VIDEO -> ShowcaseMedia.VIDEO_SAMPLES;
        };
    }

    /// The extensions the file dialog offers.
    public List<String> extensions() {
        return switch (this) {
            case AUDIO -> List.of("opus", "ogg", "oga", "mp3", "flac", "wav", "mka", "m4a", "webm");
            case VIDEO -> List.of("webm", "mkv", "mp4", "mov", "avi");
        };
    }
}
