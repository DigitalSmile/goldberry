package dev.goldberry.media.robustness;

import java.lang.foreign.Arena;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.MediaType;
import dev.goldberry.media.decoder.BuiltInDecoders;
import dev.goldberry.media.robustness.ContainerFuzzTest.FileDamage;

/// Damaged files all the way through: demuxed by the Java containers, and
/// every audio and video track a built-in decoder supports decoded from what
/// they read. Where [DecoderFuzzTest] damages packets a container read
/// cleanly, this damages the file, so the decoders also see what a container
/// makes of a broken header: a codec configuration cut in two, a rate or a
/// channel count nobody would write.
@Tag("robustness")
@DisplayName("Damaged files, demuxed and decoded")
@Timeout(value = 10, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class EndToEndFuzzTest {

    /// One file per decoder.
    static final List<String> FIXTURES = List.of(
            "clip-av1.mkv",
            "clip-av1.mp4",
            "clip-vp8.webm",
            "clip-vp9.webm",
            "sticker-vp9-alpha.webm",
            "clip-theora.ogv",
            "tone-opus.webm",
            "tone.opus",
            "tone.ogg",
            "tone-flac.oga",
            "tone.flac",
            "tone-flac.mp4",
            "tone.mp3",
            "tones-two-tracks.mkv",
            ContainerFuzzTest.WAV);

    @BeforeEach
    void requireNatives() {
        MediaNativesRequirement.enforce();
    }

    static Stream<Arguments> fixturesAndDamage() {
        return FIXTURES.stream()
                .flatMap(fixture -> Stream.of(FileDamage.values()).map(damage -> Arguments.of(fixture, damage)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("fixturesAndDamage")
    @DisplayName("what a container reads from a damaged file decodes, or fails as a MediaException")
    void damaged(String name, FileDamage damage) {
        var file = ContainerFuzzTest.bytes(name);
        var what = name + "/" + damage + "/decoded";
        for (var round = 0; round < Fuzz.rounds(); round++) {
            var random = Fuzz.random(what, round);
            var context = what + " round " + round + " (seed " + Fuzz.seed(what, round) + ")";
            var demuxed =
                    DemuxHarness.demux(name, damage.apply(file, random), DemuxHarness.Shape.PLAIN, random, context);
            for (var entry : demuxed.requests().entrySet()) {
                var request = entry.getValue();
                var type = request.track().type();
                if (type != MediaType.AUDIO && type != MediaType.VIDEO) {
                    continue;
                }
                var track = new DemuxedTrack(
                        name,
                        request.request().codec(),
                        request.request().codecName(),
                        request.request().params(),
                        request.extradata(),
                        request.request().timeBase(),
                        demuxed.samples().getOrDefault(entry.getKey(), List.of()));
                try (var arena = Arena.ofConfined()) {
                    var asked = track.request(arena, track.extradata());
                    var provider = BuiltInDecoders.providers().stream()
                            .filter(candidate -> candidate.supports(asked))
                            .findFirst();
                    if (provider.isEmpty()) {
                        continue;
                    }
                    DecodeHarness.decode(
                            provider.get(),
                            track,
                            track.samples(),
                            track.extradata(),
                            null,
                            context + " stream " + entry.getKey());
                }
            }
        }
    }
}
