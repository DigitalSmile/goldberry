package io.github.digitalsmile.goldberry.media;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.digitalsmile.goldberry.media.codec.MediaType;

/// Everything a player shows, at one instant: one immutable value, so a control
/// never reads a position from one moment and a state from the next.
///
/// @param state          where the player is
/// @param position       what is playing now, from the audio clock; the seek
///                       target while a seek settles
/// @param info           what the source holds, once it is open
/// @param error          why the player is in [PlaybackState#ERROR]
/// @param volume         the linear volume, 0 to 1
/// @param muted          whether the output is silenced
/// @param rate           how fast it plays: 1 as recorded, 2 twice as fast
/// @param audioDecoder   which decoder plays the audio: a provider's name, or
///                       `ffmpeg` for the built-in one
/// @param videoDecoder   which decoder plays the video, named the same way
/// @param bufferedAhead  how much is demuxed past the position: what plays on
///                       if the source stops delivering now. Zero before the
///                       first packet and after a seek
/// @param bufferedRanges the stretches a network source has fetched and not
///                       thrown away, mapped from bytes to time in proportion
///                       to the source's length (§4). Empty for a local file,
///                       where every byte is at hand, and for a source whose
///                       length or duration is unknown
/// @param nowPlaying     what the stream says is playing, such as an ICY
///                       station's `StreamTitle` (S6)
public record PlayerStatus(
        PlaybackState state,
        Duration position,
        Optional<MediaInfo> info,
        Optional<MediaError> error,
        float volume,
        boolean muted,
        float rate,
        Optional<String> audioDecoder,
        Optional<String> videoDecoder,
        Duration bufferedAhead,
        List<TimeRange> bufferedRanges,
        Optional<String> nowPlaying) {

    /// Before anything is opened.
    public static final PlayerStatus IDLE = new PlayerStatus(
            PlaybackState.IDLE,
            Duration.ZERO,
            Optional.empty(),
            Optional.empty(),
            1f,
            false,
            1f,
            Optional.empty(),
            Optional.empty(),
            Duration.ZERO,
            List.of(),
            Optional.empty());

    public PlayerStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(audioDecoder, "audioDecoder");
        Objects.requireNonNull(videoDecoder, "videoDecoder");
        Objects.requireNonNull(bufferedAhead, "bufferedAhead");
        Objects.requireNonNull(nowPlaying, "nowPlaying");
        if (!(volume >= 0f && volume <= 1f)) {
            throw new IllegalArgumentException("volume " + volume);
        }
        if (!(rate > 0f)) {
            throw new IllegalArgumentException("rate " + rate);
        }
        if (bufferedAhead.isNegative()) {
            throw new IllegalArgumentException("bufferedAhead " + bufferedAhead);
        }
        bufferedRanges = List.copyOf(bufferedRanges);
    }

    /// The whole presentation's length, when the source says.
    public Optional<Duration> duration() {
        return info.flatMap(MediaInfo::duration);
    }

    /// The video track the player shows, when the source has one: the default one,
    /// and never cover art.
    public Optional<Track> videoTrack() {
        return info.flatMap(described -> described.defaultTrack(MediaType.VIDEO));
    }

    /// Whether the source has a picture to show.
    public boolean hasVideo() {
        return videoTrack().isPresent();
    }

    /// Whether the source is live: it has only a "now", and a player shows
    /// `LIVE` in place of a seek bar.
    public boolean live() {
        return info.map(MediaInfo::live).orElse(false);
    }

    /// Whether a seek can do anything: the bytes can be read out of order and the
    /// presentation has a length.
    public boolean seekable() {
        return info.map(MediaInfo::seekable).orElse(false) && duration().isPresent();
    }
}
