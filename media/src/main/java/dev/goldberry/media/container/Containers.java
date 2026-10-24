package dev.goldberry.media.container;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.flac.FlacContainer;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.container.mp3.Mp3Container;
import dev.goldberry.media.container.mp4.Mp4Container;
import dev.goldberry.media.container.ogg.OggContainer;
import dev.goldberry.media.container.wav.WavContainer;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.Source;

/// Opens the container a source holds, by its first bytes.
///
/// A source is sniffed, never trusted by its name: the extension is what the
/// user called the file, the first bytes are what it is. A format this build
/// reads is opened; one it recognises and does not read is refused as
/// [MediaError.UnsupportedContainer], by name, so that the error says `AVI`
/// rather than "invalid data"; anything else is [MediaError.InvalidData].
public final class Containers {

    /// How many bytes identify every container here.
    static final int HEAD = 16;

    /// What a container's first bytes look like.
    ///
    /// @param name    what the error calls it
    /// @param matches whether the first bytes are this container's
    record Signature(String name, Predicate<byte[]> matches) {}

    /// The containers this build reads, first bytes to opener.
    /// A container this build reads: its first bytes, and how it is opened.
    private record Reader(Signature signature, BiFunction<Source, MediaIO, Container> open) {}

    /// The containers this build reads, first bytes to opener.
    private static final List<Reader> READ = List.of(
            new Reader(new Signature("Matroska", MatroskaContainer::isMagic), MatroskaContainer::open),
            new Reader(new Signature("MP4", Mp4Container::isMagic), Mp4Container::open),
            new Reader(new Signature("WAV", WavContainer::isMagic), WavContainer::open),
            new Reader(new Signature("Ogg", OggContainer::isMagic), OggContainer::open),
            new Reader(new Signature("FLAC", FlacContainer::isMagic), FlacContainer::open),
            new Reader(new Signature("MP3", Mp3Container::isMagic), Mp3Container::open));

    /// The containers this build recognises and does not read.
    static final List<Signature> RECOGNISED = List.of(
            new Signature(
                    "MP4",
                    head -> at(head, 4, "ftyp")
                            || at(head, 4, "moov")
                            || at(head, 4, "mdat")
                            || at(head, 4, "wide")
                            || at(head, 4, "free")
                            || at(head, 4, "skip")),
            new Signature("AVI", head -> at(head, 0, "RIFF") && at(head, 8, "AVI ")),
            new Signature(
                    "MPEG-TS",
                    head -> head.length > 0
                            && (head[0] & 0xFF) == 0x47
                            && head.length >= HEAD
                            && (head[1] & 0x80) == 0),
            new Signature("FLV", head -> at(head, 0, "FLV")),
            new Signature("ASF", head -> startsWith(head, new byte[] {0x30, 0x26, (byte) 0xB2, 0x75})),
            new Signature(
                    "MP3",
                    head -> at(head, 0, "ID3")
                            || (head.length >= 2
                                    && (head[0] & 0xFF) == 0xFF
                                    && (head[1] & 0xE0) == 0xE0
                                    && (head[1] & 0x18) != 0x08
                                    && (head[1] & 0x06) != 0)));

    private Containers() {}

    /// The names of the containers this build reads, as
    /// [dev.goldberry.media.MediaCapabilities#demuxers()] lists them: FFmpeg's
    /// names for them, which is what applications asked for before.
    public static List<String> readable() {
        return List.of("matroska", "webm", "mp4", "ogg", "flac", "wav", "mp3");
    }

    /// Opens the container over `io`, which is at its start.
    ///
    /// @throws MediaException [MediaError.UnsupportedContainer] for a format this
    ///                        build names and does not read,
    ///                        [MediaError.InvalidData] for bytes no container
    ///                        here recognises, [MediaError.Io] when a read fails
    public static Container open(Source source, MediaIO io) {
        var head = head(io);
        // A stream that cannot be rewound gets its head back in front of it.
        var bytes = io.isSeekable() ? io : new PushbackIO(io, head);
        for (var reader : READ) {
            if (reader.signature().matches().test(head)) {
                return reader.open().apply(source, bytes);
            }
        }
        throw new MediaException(identify(head)
                .<MediaError>map(MediaError.UnsupportedContainer::new)
                .orElse(new MediaError.InvalidData("not a container this build reads: " + source.uri())));
    }

    /// The name of the container `head` begins, among the ones this build
    /// recognises and does not read.
    public static Optional<String> identify(byte[] head) {
        for (var signature : RECOGNISED) {
            if (signature.matches().test(head)) {
                return Optional.of(signature.name());
            }
        }
        return Optional.empty();
    }

    /// The first [#HEAD] bytes of `io`, read and, where it seeks, rewound.
    private static byte[] head(MediaIO io) {
        var start = io.position();
        var buffer = ByteBuffer.allocate(HEAD);
        try {
            while (buffer.hasRemaining()) {
                if (io.read(buffer) < 0) {
                    break;
                }
            }
            if (io.isSeekable()) {
                io.seek(start);
            }
        } catch (IOException e) {
            throw ByteReader.failure(e);
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        return head.length >= magic.length && Arrays.equals(head, 0, magic.length, magic, 0, magic.length);
    }

    private static boolean at(byte[] head, int offset, String ascii) {
        var bytes = ascii.getBytes(StandardCharsets.US_ASCII);
        return head.length >= offset + bytes.length
                && Arrays.equals(head, offset, offset + bytes.length, bytes, 0, bytes.length);
    }
}
