package dev.goldberry.media.robustness;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.goldberry.media.Wav;
import dev.goldberry.media.container.ClipFixtures;

/// Every container fed damaged files: bits flipped in the headers and
/// anywhere, bytes overwritten, size fields set to what parsers choke on,
/// bytes inserted and removed, the file cut short at every length, its body
/// replaced by noise, and all of it read in short chunks, from a source of
/// unknown size, or from one that cannot seek.
///
/// Each open, read to the end, and seek either works or fails as a
/// `MediaException`; nothing else, and the reading ends ([DemuxHarness]). No
/// library is needed: the containers are Java.
@Tag("robustness")
@DisplayName("Containers under damaged files")
@Timeout(value = 10, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ContainerFuzzTest {

    /// PCM written here, which no fixture file is.
    static final String WAV = "tone.wav";

    /// Every container fixture: Matroska and WebM with video, audio, subtitles
    /// and two tracks; MP4 plain, fragmented, fast-start and as M4A with a
    /// cover; Ogg with Vorbis, Opus and FLAC; raw FLAC and MP3 with and without
    /// a cover; WAV; and the containers this build names and refuses.
    static final List<String> FIXTURES = List.of(
            "clip-av1.mkv",
            "clip-vp9.webm",
            "sticker-vp9-alpha.webm",
            "clip-vp9-subs.mkv",
            "tones-two-tracks.mkv",
            "tone-flac.mkv",
            "tone-opus.webm",
            "clip-av1.mp4",
            "clip-av1-fragmented.mp4",
            "clip-h264-aac.mp4",
            "tone-flac-faststart.mp4",
            "tone-opus.mp4",
            "tone-cover.m4a",
            "tone.ogg",
            "tone.opus",
            "tone-flac.oga",
            "tone.flac",
            "tone-cover.flac",
            "tone.mp3",
            "tone-cover.mp3",
            "tone-chapters.mkv",
            "tone-chapters.m4a",
            "tone-chapters.mp3",
            "tone-chapters.opus",
            "tone-chapters.flac",
            WAV,
            "clip-xvid-ac3.avi",
            "clip-mpeg2.ts");

    /// One kind of damage to a whole file.
    enum FileDamage {
        HEADER_BIT_FLIPS {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                return Fuzz.flipBits(file, random, 1 + random.nextInt(8), 0, Math.min(file.length, 1024));
            }
        },
        BIT_FLIPS {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                return Fuzz.flipBits(file, random, 1 + random.nextInt(32), 0, file.length);
            }
        },
        OVERWRITES {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                return Fuzz.overwrite(file, random, 1 + random.nextInt(8));
            }
        },
        HEADER_SIZE_FIELDS {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                return Fuzz.sizeFields(file, random, 1 + random.nextInt(3), 2048);
            }
        },
        SIZE_FIELDS {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                return Fuzz.sizeFields(file, random, 1 + random.nextInt(6), file.length);
            }
        },
        SHIFTED {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                return Fuzz.shift(file, random);
            }
        },
        TRUNCATED {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                return Fuzz.truncate(file, random.nextInt(file.length + 1));
            }
        },
        NOISE_BODY {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                // The magic kept, so that the sniffing hands the noise to the
                // container it would have.
                var kept = Math.min(file.length, 16 + random.nextInt(512));
                var noisy = Fuzz.garbage(random, file.length);
                System.arraycopy(file, 0, noisy, 0, kept);
                return noisy;
            }
        },
        MIXED {
            @Override
            byte[] apply(byte[] file, SplittableRandom random) {
                var damaged = file;
                for (var times = 1 + random.nextInt(4); times > 0; times--) {
                    damaged = values()[random.nextInt(ordinal())].apply(damaged, random);
                }
                return damaged;
            }
        };

        abstract byte[] apply(byte[] file, SplittableRandom random);
    }

    static byte[] bytes(String name) {
        return name.equals(WAV) ? Wav.sine(44_100, 2, 22_050, 440, 12_000) : ClipFixtures.bytes(name);
    }

    static Stream<String> fixtures() {
        return FIXTURES.stream();
    }

    static Stream<Arguments> fixturesAndDamage() {
        return FIXTURES.stream()
                .flatMap(fixture -> Stream.of(FileDamage.values()).map(damage -> Arguments.of(fixture, damage)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("the undamaged fixture reads to its end, in every shape of source")
    void undamaged(String name) {
        var file = bytes(name);
        var refused = name.endsWith(".avi") || name.endsWith(".ts");
        for (var shape : List.of(
                DemuxHarness.Shape.PLAIN,
                new DemuxHarness.Shape(7, true, true),
                new DemuxHarness.Shape(Integer.MAX_VALUE, true, false),
                new DemuxHarness.Shape(4096, false, false))) {
            var demuxed = DemuxHarness.demux(name, file, shape, Fuzz.random(name, 0), name + " " + shape);
            if (refused) {
                assertTrue(demuxed.failed(), "refused");
            } else if (shape.seekable()) {
                assertFalse(demuxed.failed(), name + " " + shape + " fails undamaged");
            }
        }
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("fixturesAndDamage")
    @DisplayName("a damaged file reads, or fails as a MediaException, and the reading ends")
    void damaged(String name, FileDamage damage) {
        var file = bytes(name);
        var what = name + "/" + damage;
        for (var round = 0; round < 4 * Fuzz.rounds(); round++) {
            var random = Fuzz.random(what, round);
            var damaged = damage.apply(file, random);
            var shape = DemuxHarness.Shape.any(random);
            DemuxHarness.demux(
                    name,
                    damaged,
                    shape,
                    random,
                    what + " round " + round + " (seed " + Fuzz.seed(what, round) + ", " + shape + ")");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("a file cut at every length of its headers, and at steps through the rest, fails cleanly")
    void cutAtEveryLength(String name) {
        var file = bytes(name);
        var random = Fuzz.random(name + "/cut", 0);
        var step = Math.max(1, file.length / (16 * Fuzz.rounds()));
        for (var length = 0; length < file.length; length = length < 512 ? length + 1 : length + step) {
            DemuxHarness.demux(
                    name, Fuzz.truncate(file, length), DemuxHarness.Shape.PLAIN, random, name + " cut at " + length);
        }
    }
}
