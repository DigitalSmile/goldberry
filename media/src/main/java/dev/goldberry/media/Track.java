package dev.goldberry.media;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.TrackParams;

/// One stream of a source: an audio, video or subtitle track, or something the
/// container carries that is not played.
///
/// @param index           the stream's index in the container, which is how a
///                        track is selected
/// @param codec           the codec, or [CodecId#UNKNOWN]
/// @param codecName       FFmpeg's name for the codec. It is the same as
///                        `codec.ffmpegName()` for every codec but
///                        [CodecId#UNKNOWN], whose name is only here
/// @param params          what a decoder needs, by kind of stream
/// @param duration        the track's own duration, when the container records it
/// @param isDefault       whether the container marks this track as the one to
///                        play when nobody chooses
/// @param attachedPicture whether this "video" track is one still picture: the
///                        cover art of an audio file
/// @param language        the track's language as the container tags it, most
///                        often an ISO 639-2 code (`eng`, `fra`); empty when
///                        untagged or `und`
/// @param title           the track's own name, such as "Director's commentary",
///                        when the container gives one
/// @param frameCount      how many pictures a video track holds, when the
///                        container records it: MP4 does, Matroska and WebM do
///                        not, and there [dev.goldberry.media.codec.FrameRate#framesIn]
///                        of the duration is the count
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public record Track(
        int index,
        CodecId codec,
        String codecName,
        TrackParams params,
        Optional<Duration> duration,
        boolean isDefault,
        boolean attachedPicture,
        Optional<String> language,
        Optional<String> title,
        OptionalLong frameCount) {

    public Track {
        if (index < 0) {
            throw new IllegalArgumentException("negative stream index " + index);
        }
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(codecName, "codecName");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(frameCount, "frameCount");
    }

    /// A track whose count of pictures is not known: every component but that.
    public Track(
            int index,
            CodecId codec,
            String codecName,
            TrackParams params,
            Optional<Duration> duration,
            boolean isDefault,
            boolean attachedPicture,
            Optional<String> language,
            Optional<String> title) {
        this(
                index,
                codec,
                codecName,
                params,
                duration,
                isDefault,
                attachedPicture,
                language,
                title,
                OptionalLong.empty());
    }

    /// A track with no language and no title: every component but those two.
    public Track(
            int index,
            CodecId codec,
            String codecName,
            TrackParams params,
            Optional<Duration> duration,
            boolean isDefault,
            boolean attachedPicture) {
        this(index, codec, codecName, params, duration, isDefault, attachedPicture, Optional.empty(), Optional.empty());
    }

    /// The kind of stream: the parameters' kind.
    public MediaType type() {
        return params.type();
    }
}
