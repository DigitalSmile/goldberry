package dev.goldberry.media.robustness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.Wav;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.robustness.DemuxedTrack.Sample;

/// Every built-in decoder fed damaged packets from the fixtures: bits flipped,
/// bytes overwritten, size fields set to what parsers choke on, packets cut
/// short, emptied, replaced by noise, reordered, repeated, keyframes dropped,
/// the codec configuration damaged at open, and nonsense timing.
///
/// Each decoder may decode, decode nothing, or throw a `MediaException`; never
/// crash, never throw anything else, and always close ([DecodeHarness]). The
/// rounds are few in `test` and many in the sanitizer lane, where a read past
/// the end of a buffer inside a codec stops the run with a report
/// ([Fuzz#ROUNDS_PROPERTY]).
@Tag("robustness")
@DisplayName("Decoders under damaged input")
@Timeout(value = 10, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class DecoderFuzzTest {

    /// The fixtures, one per decoder and per path through it: dav1d from
    /// Matroska and MP4; libvpx in VP8, 8- and 10-bit VP9, 4:4:4 VP9 (the
    /// subsampling path), and VP9 with alpha; libtheora from Ogg and Matroska,
    /// and in 4:4:4; Opus from Ogg and WebM, Vorbis, FLAC with each of the three
    /// header records, MP3, and PCM.
    static final List<Fixture> FIXTURES = List.of(
            new Fixture("clip-av1.mkv", MediaType.VIDEO),
            new Fixture("clip-av1.mp4", MediaType.VIDEO),
            new Fixture("clip-vp8.webm", MediaType.VIDEO),
            new Fixture("clip-vp9.webm", MediaType.VIDEO),
            new Fixture("clip-vp9-10bit.webm", MediaType.VIDEO),
            new Fixture("clip-vp9-444.webm", MediaType.VIDEO),
            new Fixture("clip-vp9-444-10bit.webm", MediaType.VIDEO),
            new Fixture("sticker-vp9-alpha.webm", MediaType.VIDEO),
            new Fixture("clip-theora.ogv", MediaType.VIDEO),
            new Fixture("clip-theora.mkv", MediaType.VIDEO),
            new Fixture("clip-theora-444.ogv", MediaType.VIDEO),
            new Fixture("tone.opus", MediaType.AUDIO),
            new Fixture("tone-opus.webm", MediaType.AUDIO),
            new Fixture("tone.ogg", MediaType.AUDIO),
            new Fixture("tone.flac", MediaType.AUDIO),
            new Fixture("tone-flac.mkv", MediaType.AUDIO),
            new Fixture("tone-flac.mp4", MediaType.AUDIO),
            new Fixture("tone.mp3", MediaType.AUDIO),
            new Fixture(Fixture.WAV, MediaType.AUDIO));

    private static final Map<String, DemuxedTrack> TRACKS = new ConcurrentHashMap<>();

    /// A fixture and the kind of track in it the decoder is fed.
    ///
    /// @param name the file under the fixtures, or [#WAV] for PCM written here
    /// @param type the track's kind
    record Fixture(String name, MediaType type) {

        /// A second of 16-bit stereo, which no fixture file is.
        static final String WAV = "tone.wav";

        DemuxedTrack track() {
            return TRACKS.computeIfAbsent(name, _ -> {
                var bytes = name.equals(WAV) ? Wav.sine(48_000, 2, 48_000, 440, 12_000) : ClipFixtures.bytes(name);
                return DemuxedTrack.of(name, bytes, type);
            });
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /// One kind of damage: what it does to the packets, to the codec
    /// configuration, and whether the decoder is flushed between packets.
    enum Damage {
        BIT_FLIPS {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                return each(
                        samples, random, 3, data -> Fuzz.flipBits(data, random, 1 + random.nextInt(8), 0, data.length));
            }
        },
        OVERWRITES {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                return each(samples, random, 3, data -> Fuzz.overwrite(data, random, 1 + random.nextInt(4)));
            }
        },
        SIZE_FIELDS {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                // The first bytes of a packet are its headers: OBU sizes, a
                // frame's dimensions, Opus's TOC, a FLAC frame's header.
                return each(samples, random, 3, data -> Fuzz.sizeFields(data, random, 1 + random.nextInt(2), 64));
            }
        },
        TRUNCATION {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                return each(samples, random, 3, data -> Fuzz.truncate(data, random.nextInt(data.length + 1)));
            }
        },
        EMPTY_PACKETS {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                var out = new ArrayList<Sample>();
                for (var sample : samples) {
                    if (random.nextInt(4) == 0) {
                        out.add(sample.withData(new byte[0]));
                    }
                    out.add(random.nextInt(4) == 0 ? sample.withData(new byte[0]) : sample);
                }
                return out;
            }
        },
        GARBAGE {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                var out = new ArrayList<Sample>();
                for (var sample : samples) {
                    if (random.nextInt(8) == 0) {
                        out.add(sample.withData(Fuzz.garbage(random, random.nextInt(4096))));
                    }
                    var length = sample.data().length;
                    out.add(
                            random.nextInt(4) == 0
                                    ? sample.withData(Fuzz.garbage(random, random.nextInt(2 * length + 1)))
                                    : sample);
                }
                return out;
            }
        },
        REORDERED {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                var out = new ArrayList<>(samples);
                for (var i = 0; i < 1 + out.size() / 8; i++) {
                    var from = random.nextInt(out.size());
                    var to = Math.min(out.size(), from + 2 + random.nextInt(7));
                    Collections.shuffle(out.subList(from, to), new Random(random.nextLong()));
                }
                return out;
            }
        },
        DUPLICATED {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                var out = new ArrayList<Sample>();
                for (var sample : samples) {
                    out.add(sample);
                    for (var copies = random.nextInt(4) == 0 ? 1 + random.nextInt(3) : 0; copies > 0; copies--) {
                        out.add(sample);
                    }
                }
                return out;
            }
        },
        KEYFRAMES_DROPPED {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                // The first keyframe always; every keyframe, or a quarter of
                // everything, in some rounds.
                var all = random.nextBoolean();
                var some = random.nextBoolean();
                var out = new ArrayList<Sample>();
                var first = true;
                for (var sample : samples) {
                    if (sample.keyframe() && (first || all)) {
                        first = false;
                        continue;
                    }
                    if (some && random.nextInt(4) == 0) {
                        continue;
                    }
                    out.add(sample);
                }
                return out;
            }
        },
        CODEC_CONFIGURATION {
            @Override
            byte[] extradata(byte[] record, SplittableRandom random) {
                return switch (random.nextInt(4)) {
                    case 0 -> new byte[0];
                    case 1 -> Fuzz.garbage(random, random.nextInt(256));
                    case 2 -> Fuzz.truncate(record, random.nextInt(record.length + 1));
                    default -> Fuzz.anyDamage(record, random);
                };
            }
        },
        TIMING {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                long[] times = {Packet.NO_TIMESTAMP, -1, 0, Long.MAX_VALUE, Long.MIN_VALUE + 1, -1_000_000_000L};
                var out = new ArrayList<Sample>();
                for (var sample : samples) {
                    var pts = random.nextBoolean() ? times[random.nextInt(times.length)] : random.nextLong();
                    var duration = random.nextBoolean() ? times[random.nextInt(times.length)] : random.nextLong();
                    out.add(random.nextInt(3) == 0 ? sample : sample.withTiming(pts, duration));
                }
                return out;
            }

            @Override
            boolean flushes() {
                return true;
            }
        },
        ALPHA {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                // A track without alpha gets some anyway, the picture's own
                // bytes or noise; one with alpha has it damaged.
                var out = new ArrayList<Sample>();
                for (var sample : samples) {
                    if (random.nextInt(3) != 0) {
                        out.add(sample);
                        continue;
                    }
                    var alpha = sample.alpha().length > 0
                            ? Fuzz.anyDamage(sample.alpha(), random)
                            : random.nextBoolean() ? sample.data() : Fuzz.garbage(random, random.nextInt(512));
                    out.add(new Sample(
                            sample.data(), alpha, sample.pts(), sample.dts(), sample.duration(), sample.keyframe()));
                }
                return out;
            }
        },
        MIXED {
            @Override
            List<Sample> packets(List<Sample> samples, SplittableRandom random) {
                var damaged = each(samples, random, 4, data -> Fuzz.anyDamage(data, random));
                var kinds = values();
                var other = kinds[random.nextInt(ordinal())];
                return other.packets(damaged, random);
            }

            @Override
            byte[] extradata(byte[] record, SplittableRandom random) {
                return random.nextInt(4) == 0 ? CODEC_CONFIGURATION.extradata(record, random) : record;
            }

            @Override
            boolean flushes() {
                return true;
            }
        };

        /// The packets, damaged.
        List<Sample> packets(List<Sample> samples, SplittableRandom random) {
            return samples;
        }

        /// The codec configuration, damaged.
        byte[] extradata(byte[] record, SplittableRandom random) {
            return record;
        }

        /// Whether the decoder is flushed now and then between packets.
        boolean flushes() {
            return false;
        }

        /// `samples` with one in `odds` damaged by `damage`.
        static List<Sample> each(
                List<Sample> samples, SplittableRandom random, int odds, UnaryOperator<byte[]> damage) {
            var out = new ArrayList<Sample>(samples.size());
            for (var sample : samples) {
                out.add(random.nextInt(odds) == 0 ? sample.withData(damage.apply(sample.data())) : sample);
            }
            return out;
        }
    }

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    static Stream<Fixture> fixtures() {
        return FIXTURES.stream();
    }

    static Stream<Arguments> fixturesAndDamage() {
        return FIXTURES.stream()
                .flatMap(fixture -> Stream.of(Damage.values()).map(damage -> Arguments.of(fixture, damage)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("the undamaged fixture decodes without a failure, through the same harness")
    void undamaged(Fixture fixture) {
        var track = fixture.track();
        var provider = DecodeHarness.providerFor(track);
        var outcome =
                DecodeHarness.decode(provider, track, track.samples(), track.extradata(), null, fixture + " undamaged");
        assertEquals(0, outcome.failures(), "failures");
        assertTrue(outcome.frames() > 0, "frames");
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("fixturesAndDamage")
    @DisplayName("a damaged stream decodes, or fails as a MediaException, and the decoder closes")
    void damaged(Fixture fixture, Damage damage) {
        var track = fixture.track();
        var provider = DecodeHarness.providerFor(track);
        var what = fixture + "/" + damage;
        for (var round = 0; round < Fuzz.rounds(); round++) {
            var random = Fuzz.random(what, round);
            var context = what + " round " + round + " (seed " + Fuzz.seed(what, round) + ")";
            var packets = damage.packets(track.samples(), random);
            var extradata = damage.extradata(track.extradata(), random);
            var flushes = damage.flushes() ? random.split() : null;
            DecodeHarness.decode(provider, track, packets, extradata, flushes, context);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("a packet cut at every length, first a keyframe and then one after it, fails cleanly")
    void cutAtEveryLength(Fixture fixture) {
        var track = fixture.track();
        var provider = DecodeHarness.providerFor(track);
        var samples = track.samples();
        var random = Fuzz.random(fixture + "/cut", 0);
        var keyframe = 0;
        while (keyframe < samples.size() - 1 && !samples.get(keyframe).keyframe()) {
            keyframe++;
        }
        var later =
                Math.min(samples.size() - 1, keyframe + 1 + random.nextInt(Math.max(1, samples.size() - keyframe - 1)));
        for (var cut : new int[] {keyframe, later}) {
            var length = samples.get(cut).data().length;
            var step = Math.max(1, length / (8 * Fuzz.rounds()));
            for (var at = 0; at < length; at += step) {
                var packets = new ArrayList<>(samples.subList(0, cut));
                packets.add(
                        samples.get(cut).withData(Fuzz.truncate(samples.get(cut).data(), at)));
                packets.addAll(samples.subList(cut + 1, Math.min(samples.size(), cut + 3)));
                DecodeHarness.decode(
                        provider,
                        track,
                        packets,
                        track.extradata(),
                        null,
                        fixture + " packet " + cut + " cut at " + at);
            }
        }
    }
}
