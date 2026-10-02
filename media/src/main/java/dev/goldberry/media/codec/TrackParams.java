package dev.goldberry.media.codec;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/// What a decoder needs to know about a track before it sees a packet: what
/// FFmpeg's `AVCodecParameters` holds, in Goldberry's words.
///
/// Sealed, one record per kind of stream, so a caller switches over it and the
/// compiler checks that every kind is handled:
///
/// ```java
/// String describe(TrackParams params) {
///     return switch (params) {
///         case TrackParams.Video v -> v.width() + "×" + v.height();
///         case TrackParams.Audio a -> a.sampleRate() + " Hz, " + a.channels() + " ch";
///         case TrackParams.Subtitle _ -> "subtitles";
///         case TrackParams.Other o -> o.type().name().toLowerCase();
///     };
/// }
/// ```
///
/// A value FFmpeg reports as unknown is empty here rather than FFmpeg's sentinel:
/// no `-99` for a profile, and no zero for a bit rate.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public sealed interface TrackParams {

    /// The kind of stream these parameters describe.
    MediaType type();

    /// A video track.
    ///
    /// @param width       coded width in pixels
    /// @param height      coded height in pixels
    /// @param pixelFormat FFmpeg's name for the decoded pixel format, such as
    ///                    `yuv420p` or `yuv420p10le`, when the container says
    /// @param profile     the codec profile number, when known
    /// @param level       the codec level number, when known
    /// @param bitRate     the average bit rate in bits per second, when known
    record Video(
            int width,
            int height,
            Optional<String> pixelFormat,
            OptionalInt profile,
            OptionalInt level,
            OptionalLong bitRate)
            implements TrackParams {

        public Video {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("negative size " + width + "×" + height);
            }
            Objects.requireNonNull(pixelFormat, "pixelFormat");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(bitRate, "bitRate");
        }

        @Override
        public MediaType type() {
            return MediaType.VIDEO;
        }
    }

    /// An audio track.
    ///
    /// @param sampleRate   samples per second per channel
    /// @param channels     the channel count
    /// @param sampleFormat FFmpeg's name for the decoded sample format, such as
    ///                     `fltp` or `s16`, when the container says
    /// @param profile      the codec profile number, when known
    /// @param bitRate      the average bit rate in bits per second, when known
    record Audio(int sampleRate, int channels, Optional<String> sampleFormat, OptionalInt profile, OptionalLong bitRate)
            implements TrackParams {

        public Audio {
            if (sampleRate < 0 || channels < 0) {
                throw new IllegalArgumentException("negative rate or channel count: " + sampleRate + ", " + channels);
            }
            Objects.requireNonNull(sampleFormat, "sampleFormat");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(bitRate, "bitRate");
        }

        @Override
        public MediaType type() {
            return MediaType.AUDIO;
        }
    }

    /// A subtitle track. Text subtitles need nothing before their first packet.
    record Subtitle() implements TrackParams {

        @Override
        public MediaType type() {
            return MediaType.SUBTITLE;
        }
    }

    /// A track that is not played: an attachment, or data.
    ///
    /// @param type [MediaType#ATTACHMENT] or [MediaType#DATA]
    record Other(MediaType type) implements TrackParams {

        public Other {
            Objects.requireNonNull(type, "type");
            if (type != MediaType.ATTACHMENT && type != MediaType.DATA) {
                throw new IllegalArgumentException(type + " has parameters of its own");
            }
        }
    }
}
