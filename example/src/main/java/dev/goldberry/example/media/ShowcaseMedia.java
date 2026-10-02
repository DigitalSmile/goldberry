package dev.goldberry.example.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.io.HttpIO;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.Source;
import dev.goldberry.media.platform.PlatformDecoders;

/// Where the **Audio** and **Video** screens' sounds and pictures come from, and
/// the players that play them.
///
/// Every sample is reached through a [MediaIOProvider], which is the point: FFmpeg
/// performs no I/O in Goldberry, so a protocol is something an application
/// writes in a few lines. Four schemes are served here:
///
/// - `showcase:` is a clip bundled in this jar, read as a seekable stream.
/// - `live:` is the same clip read **front to back only**, with no length: what
///   an internet radio stream looks like to the engine. The player shows `LIVE`
///   in place of the seek bar, and the title it says is playing.
/// - `served:` is a bundled clip fetched over real HTTP from [ShowcaseServer], a
///   throttled server on the loopback address, through Goldberry's own
///   [HttpIO], the one behind `http:` and `https:`. So the network path (range
///   requests, the read-ahead cache, the seek bar's buffered stretches,
///   BUFFERING) is shown offline.
/// - `generated:` is bytes made in Java: a chime in a WAV, which this
///   application's own [JavaPcmDecoder] plays, and bytes that are not media at
///   all.
///
/// `file:`, `http:` and `https:` need no provider; they are built in.
///
/// Read more:
/// [The network](https://goldberry.dev/docs/components/media.html#tracks-subtitles-and-the-network).
public final class ShowcaseMedia implements MediaIOProvider {

    /// One entry of the screen's source picker.
    ///
    /// @param key    what the picker reports
    /// @param title  what the picker shows
    /// @param note   one sentence on what the sample demonstrates
    /// @param source what the player opens
    public record Sample(String key, String title, String note, Source source) {}

    /// The Audio screen's samples, in picker order.
    public static final List<Sample> AUDIO_SAMPLES = List.of(
            new Sample(
                    "opus",
                    "Opus in Ogg",
                    "The codec most new audio is in: Opus, in an Ogg container.",
                    showcase("arpeggio.opus")),
            new Sample(
                    "vorbis",
                    "Vorbis in Ogg",
                    "Opus's predecessor, still everywhere in games and on the web.",
                    showcase("arpeggio.ogg")),
            new Sample(
                    "mp3",
                    "MP3 with cover art",
                    "MP3's patents expired in 2017. This file carries a PNG as its cover art, which the"
                            + " Tracks card lists as an attached picture and never plays as video.",
                    showcase("arpeggio.mp3")),
            new Sample(
                    "flac",
                    "FLAC, mono at 24 kHz",
                    "Lossless, and in a format the speaker does not take: one channel at 24 kHz, converted to"
                            + " 48 kHz stereo in the engine's one resampling pass.",
                    showcase("arpeggio.flac")),
            new Sample(
                    "voices",
                    "Two voices, one file",
                    "Two Opus tracks in Matroska audio: the arpeggio, tagged English and titled, and the octave"
                            + " below it, tagged French. The player grows a track menu that names both by title"
                            + " and language; switching keeps the position, to the sample.",
                    showcase("arpeggio-two-voices.mka")),
            new Sample(
                    "live",
                    "A live stream",
                    "The Opus clip, served with no length and no seeking, the way an internet radio stream"
                            + " arrives. The seek bar gives way to LIVE, and the station's title shows over the"
                            + " controls, as an ICY stream's StreamTitle would.",
                    Source.of(URI.create("live:///arpeggio.opus"))),
            new Sample(
                    "served",
                    "Over HTTP, throttled",
                    "The Opus clip from an HTTP server inside this application, sent at 64 KB a second:"
                            + " Goldberry's own HTTP reader, with range requests and a read-ahead cache. The seek"
                            + " bar shades what has arrived; seek past it and the player buffers, then plays on.",
                    served("arpeggio.opus")),
            new Sample(
                    "java",
                    "Decoded in Java",
                    "A chime written as PCM in a WAV, and decoded by this application's own DecoderProvider"
                            + " rather than by FFmpeg. The Status card names the decoder.",
                    Source.of(URI.create("generated:///chime.wav"))),
            new Sample(
                    "broken",
                    "Not media at all",
                    "Bytes no demuxer recognises. The error says so.",
                    Source.of(URI.create("generated:///notes.bin"))),
            new Sample(
                    "missing",
                    "A file that is not there",
                    "A read that fails is an I/O error, with the message the file system gave.",
                    Source.of(Path.of(System.getProperty("java.io.tmpdir"), "goldberry-no-such-file.opus"))));

    /// The Video screen's samples, in picker order.
    public static final List<Sample> VIDEO_SAMPLES = List.of(
            new Sample(
                    "vp9",
                    "VP9 and Opus in WebM",
                    "A Mandelbrot zoom in VP9 with the arpeggio in Opus, in WebM. The pictures are timed by the"
                            + " audio clock, converted to BGRA as they are decoded, and drawn by the media-player"
                            + " above.",
                    showcase("mandelbrot.webm")),
            new Sample(
                    "subtitled",
                    "Subtitles: SubRip and ASS",
                    "The same zoom with two subtitle tracks, SubRip in English and ASS in French, read in"
                            + " Java and drawn over the picture. Pick one from the player's subtitles menu or the"
                            + " Subtitles card; the ASS styling is taken down to plain lines.",
                    showcase("mandelbrot-subtitled.mkv")),
            new Sample(
                    "angles",
                    "Two angles, two voices",
                    "Two video tracks, the zoom and a Sierpinski carpet at 4:3, over two audio tracks. The"
                            + " player grows a menu for each; a new angle comes in on the picture that covers the"
                            + " position, with no black frame between.",
                    showcase("two-angles.mkv")),
            new Sample(
                    "served",
                    "Over HTTP, throttled",
                    "The zoom from an HTTP server inside this application, sent at 64 KB a second, a little"
                            + " faster than it plays. The seek bar shades what has arrived; seek past it and the"
                            + " player buffers, then plays on.",
                    served("mandelbrot.webm")),
            new Sample(
                    "av1",
                    "AV1 in Matroska, no sound",
                    "The Game of Life in AV1, decoded by dav1d, with no audio track: the pictures are timed by"
                            + " a free-running clock instead.",
                    showcase("life.mkv")),
            new Sample(
                    "h264",
                    "H.264 and AAC",
                    "Patent-pool codecs, which the published natives do not build, by decision. The"
                            + " system's own decoders play them -- VideoToolbox and AudioToolbox on macOS,"
                            + " GStreamer on Linux, Media Foundation on Windows -- and the Status card names"
                            + " them. Without them the file opens, its tracks are listed, and the error names"
                            + " the codec.",
                    showcase("h264-aac.mp4")));

    /// A subtitle file the Video screen's Subtitles card can load beside a source.
    ///
    /// @param key    what the card's buttons report
    /// @param title  what they say
    /// @param source the file, read through the player's protocols
    public record SubtitleFile(String key, String title, Source source) {}

    /// The bundled subtitle files: the Mandelbrot clip's cues as SubRip and as
    /// WebVTT, which the Subtitles card loads over whatever is playing.
    public static final List<SubtitleFile> SUBTITLE_FILES = List.of(
            new SubtitleFile("srt", "mandelbrot.srt", showcase("mandelbrot.srt")),
            new SubtitleFile("vtt", "mandelbrot.vtt", showcase("mandelbrot.vtt")));

    /// Stops what the showcase's protocols started: the local HTTP server, if a
    /// network sample was played.
    public static void shutdown() {
        ShowcaseServer.stopShared();
    }

    /// The Audio screen's player, which the screen and its markup share: every
    /// protocol above, the Java decoder, which takes PCM only while its switch is
    /// on, and the operating system's decoders, for an AAC file opened from disk.
    public static MediaPlayer audioPlayer(JavaPcmDecoder decoder) {
        var providers = new ArrayList<DecoderProvider>();
        providers.add(decoder);
        providers.addAll(PlatformDecoders.providers());
        return MediaPlayer.builder()
                .ioProviders(List.of(new ShowcaseMedia()))
                .decoderProviders(providers)
                .build();
    }

    /// The Video screen's player: every protocol above, FFmpeg's decoders and the
    /// operating system's (H.264, HEVC, AAC and the AC-3 pair on macOS), and the
    /// GPU's video engine where there is one, which the screen's Hardware card
    /// switches off and on.
    public static MediaPlayer videoPlayer() {
        return MediaPlayer.builder()
                .ioProviders(List.of(new ShowcaseMedia()))
                .decoderProviders(PlatformDecoders.providers())
                .hardwareDecoding(HardwareDecoding.AUTO)
                .build();
    }

    /// What the live sample says is playing.
    static final String LIVE_TITLE = "Goldberry Radio - Arpeggio in A minor";

    private static Source showcase(String name) {
        return Source.of(URI.create("showcase:///" + name));
    }

    private static Source served(String name) {
        return Source.of(URI.create("served:///" + name));
    }

    @Override
    public Set<String> schemes() {
        return Set.of("showcase", "live", "served", "generated");
    }

    @Override
    public MediaIO open(Source source) throws IOException {
        var name = source.fileName().orElseThrow(() -> new IOException("no clip named in " + source.uri()));
        return switch (source.scheme()) {
            case "showcase" -> new Bytes(resource(name), true);
            case "live" -> new Bytes(resource(name), false);
            case "served" -> HttpIO.open(Source.of(ShowcaseServer.shared().uri(name)));
            case "generated" -> new Bytes(generated(name), true);
            default -> throw new IOException("not a showcase scheme: " + source.scheme());
        };
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = ShowcaseMedia.class.getResourceAsStream("/dev/goldberry/example/media/" + name)) {
            if (in == null) {
                throw new IOException("the showcase has no clip called " + name);
            }
            return in.readAllBytes();
        }
    }

    private static byte[] generated(String name) throws IOException {
        return switch (name) {
            case "chime.wav" -> chime();
            case "notes.bin" ->
                "These are notes about music, not music.\n".repeat(200).getBytes(StandardCharsets.UTF_8);
            default -> throw new IOException("nothing is generated under " + name);
        };
    }

    /// Six seconds of a two-note chime, 44.1 kHz stereo 16-bit PCM in a WAV.
    /// 44.1 rather than 48, so the engine resamples this one too.
    static byte[] chime() {
        var rate = 44_100;
        var frames = rate * 6;
        var buffer = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + frames * 4);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
        buffer.putShort((short) 1).putShort((short) 2).putInt(rate).putInt(rate * 4);
        buffer.putShort((short) 4).putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(frames * 4);
        double[] notes = {659.25, 523.25, 587.33, 392.00};
        for (var i = 0; i < frames; i++) {
            var t = (double) i / rate;
            var strike = (int) (t / 1.5);
            var since = t - strike * 1.5;
            var f = notes[strike % notes.length];
            var ring = Math.exp(-since * 2.2)
                    * (Math.sin(2 * Math.PI * f * t) + 0.25 * Math.sin(2 * Math.PI * 2.76 * f * t));
            var sample = (short) Math.round(ring * 0.3 * 32767);
            buffer.putShort(sample).putShort(sample);
        }
        return buffer.array();
    }

    /// A byte array as a [MediaIO]: seekable with a length, or neither.
    static final class Bytes implements MediaIO {

        private final byte[] data;
        private final boolean seekable;
        private int position;

        Bytes(byte[] data, boolean seekable) {
            this.data = Objects.requireNonNull(data, "data");
            this.seekable = seekable;
        }

        @Override
        public int read(ByteBuffer target) {
            if (position >= data.length) {
                return -1;
            }
            var count = Math.min(target.remaining(), data.length - position);
            target.put(data, position, count);
            position += count;
            return count;
        }

        @Override
        public void seek(long position) throws IOException {
            if (!seekable) {
                throw new IOException("a live stream cannot seek");
            }
            this.position = (int) Math.min(position, data.length);
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public OptionalLong size() {
            return seekable ? OptionalLong.of(data.length) : OptionalLong.empty();
        }

        @Override
        public boolean isSeekable() {
            return seekable;
        }

        /// The unseekable sample stands for a radio station, so it says it is live.
        @Override
        public boolean isLive() {
            return !seekable;
        }

        /// And, like a station, what is on.
        @Override
        public Optional<String> nowPlaying() {
            return seekable ? Optional.empty() : Optional.of(LIVE_TITLE);
        }

        @Override
        public void close() {}
    }
}
