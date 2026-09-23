package io.github.digitalsmile.goldberry.example.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

import io.github.digitalsmile.goldberry.media.MediaPlayer;
import io.github.digitalsmile.goldberry.media.io.MediaIO;
import io.github.digitalsmile.goldberry.media.io.MediaIOProvider;
import io.github.digitalsmile.goldberry.media.io.Source;

/// Where the **Media** screen's sounds come from, and the player that plays them.
///
/// Every sample is reached through a [MediaIOProvider], which is the point: FFmpeg
/// performs no I/O in Goldberry, so a protocol is something an application
/// writes in a few lines. Three schemes are served here:
///
/// - `showcase:` is a clip bundled in this jar, read as a seekable stream.
/// - `live:` is the same clip read **front to back only**, with no length: what
///   an internet radio stream looks like to the engine. The player shows `LIVE`
///   in place of the seek bar.
/// - `generated:` is bytes made in Java: a chime in a WAV, which this
///   application's own [JavaPcmDecoder] plays, and bytes that are not media at
///   all.
///
/// `file:` needs no provider; it is built in.
public final class ShowcaseMedia implements MediaIOProvider {

    /// One entry of the screen's source picker.
    ///
    /// @param key    what the picker reports
    /// @param title  what the picker shows
    /// @param note   one sentence on what the sample demonstrates
    /// @param source what the player opens
    public record Sample(String key, String title, String note, Source source) {}

    /// Every sample, in picker order.
    public static final List<Sample> SAMPLES = List.of(
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
                    "live",
                    "A live stream",
                    "The Opus clip, served with no length and no seeking, the way an internet radio stream"
                            + " arrives. The seek bar gives way to LIVE.",
                    Source.of(URI.create("live:///arpeggio.opus"))),
            new Sample(
                    "java",
                    "Decoded in Java",
                    "A chime written as PCM in a WAV, and decoded by this application's own DecoderProvider"
                            + " rather than by FFmpeg. The Status card names the decoder.",
                    Source.of(URI.create("generated:///chime.wav"))),
            new Sample(
                    "h264",
                    "H.264 and AAC",
                    "Patent-pool codecs, which the published natives do not build, by decision. The file"
                            + " opens and its tracks are listed, and the error names the codec.",
                    showcase("h264-aac.mp4")),
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

    /// The player the screen and its markup share: every protocol above, and the
    /// Java decoder, which takes PCM only while its switch is on.
    public static MediaPlayer player(JavaPcmDecoder decoder) {
        return MediaPlayer.builder()
                .ioProviders(List.of(new ShowcaseMedia()))
                .decoderProviders(List.of(decoder))
                .build();
    }

    private static Source showcase(String name) {
        return Source.of(URI.create("showcase:///" + name));
    }

    @Override
    public Set<String> schemes() {
        return Set.of("showcase", "live", "generated");
    }

    @Override
    public MediaIO open(Source source) throws IOException {
        var name = source.fileName().orElseThrow(() -> new IOException("no clip named in " + source.uri()));
        return switch (source.scheme()) {
            case "showcase" -> new Bytes(resource(name), true);
            case "live" -> new Bytes(resource(name), false);
            case "generated" -> new Bytes(generated(name), true);
            default -> throw new IOException("not a showcase scheme: " + source.scheme());
        };
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in =
                ShowcaseMedia.class.getResourceAsStream("/io/github/digitalsmile/goldberry/example/media/" + name)) {
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

        @Override
        public void close() {}
    }
}
