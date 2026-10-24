package dev.goldberry.media.engine;

import static dev.goldberry.media.HardwareDecoding.AUTO;
import static dev.goldberry.media.HardwareDecoding.OFF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.MediaNativesRequirement;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.container.ClipFixtures;
import dev.goldberry.media.container.matroska.MatroskaContainer;
import dev.goldberry.media.decoder.BuiltInDecoders;

@DisplayName("Decoders")
class DecodersTest {

    /// A provider that supports `codec` and opens a decoder that decodes nothing.
    private record Fake(String name, int priority, CodecId codec, boolean opens) implements DecoderProvider {

        @Override
        public boolean supports(DecoderRequest request) {
            return request.codec() == codec;
        }

        @Override
        public Decoder open(DecoderRequest request) {
            if (!opens) {
                throw new IllegalStateException(name + " will not open");
            }
            return new Decoder() {
                @Override
                public boolean send(Packet packet) {
                    return true;
                }

                @Override
                public void sendEnd() {}

                @Override
                public Received receive() {
                    return Received.ENDED;
                }

                @Override
                public void flush() {}

                @Override
                public void close() {}
            };
        }
    }

    private static MatroskaContainer av1() {
        return MatroskaContainer.open(ClipFixtures.source("clip-av1.mkv"), ClipFixtures.io("clip-av1.mkv"));
    }

    @Test
    @DisplayName("asks the application's providers by priority, and the built-in ones after them")
    void order() {
        var low = new Fake("low", 0, CodecId.AV1, true);
        var high = new Fake("high", 10, CodecId.AV1, true);
        var ordered = Decoders.ordered(List.of(low, high));
        assertEquals("high", ordered.get(0).name());
        assertEquals("low", ordered.get(1).name());
        assertEquals(BuiltInDecoders.providers().size() + 2, ordered.size());
        assertEquals(BuiltInDecoders.DAV1D, ordered.get(2).name());
    }

    @Test
    @DisplayName("opens with the first provider that supports the track, and skips down the ladder")
    void opensAndSkips() {
        try (var container = av1()) {
            var first = new Fake("first", 1, CodecId.AV1, true);
            var second = new Fake("second", 0, CodecId.AV1, true);
            assertEquals(
                    "first",
                    Decoders.open(container, 0, List.of(second, first), 0, AUTO).provider());
            assertEquals(
                    "second",
                    Decoders.open(container, 0, List.of(second, first), 1, AUTO).provider());
            assertTrue(Decoders.supports(container, 0, List.of(first), AUTO));
        }
    }

    @Test
    @DisplayName("passes over a provider that will not open, and names the codec when none is left")
    void refusals() {
        try (var container = av1()) {
            var broken = new Fake("broken", 5, CodecId.AV1, false);
            var opus = new Fake("opus-only", 0, CodecId.OPUS, true);
            // Stream 1 is Opus: the AV1 fakes do not apply, the Opus fake does.
            assertEquals(
                    "opus-only",
                    Decoders.open(container, 1, List.of(broken, opus), 0, AUTO).provider());
            // Past every candidate for a track nothing else decodes without the natives.
            var e = assertThrows(
                    MediaException.class,
                    () -> Decoders.open(
                            container,
                            1,
                            List.of(opus),
                            1 + BuiltInDecoders.providers().size(),
                            AUTO));
            var error = assertInstanceOf(MediaError.UnsupportedCodec.class, e.error());
            assertEquals(List.of("opus"), error.codecs());
        }
    }

    @Test
    @DisplayName("a track no provider supports is unsupported, unless a built-in decoder has it")
    void builtIns() {
        try (var container = av1()) {
            var none = new Fake("none", 0, CodecId.VP8, true);
            MediaNativesRequirement.enforce();
            assertTrue(Decoders.supports(container, 0, List.of(none), AUTO), "dav1d is built in");
            var resolved = Decoders.open(container, 0, List.of(none), 0, AUTO);
            assertEquals(BuiltInDecoders.DAV1D, resolved.provider());
            resolved.decoder().close();
        }
    }

    /// A provider with only a hardware decoder for AV1, which says no to a
    /// request that is software only, and remembers every request it saw.
    private record HardwareOnly(List<DecoderRequest> seen) implements DecoderProvider {

        @Override
        public String name() {
            return "hardware-only";
        }

        @Override
        public boolean supports(DecoderRequest request) {
            seen.add(request);
            return request.codec() == CodecId.AV1 && !request.softwareOnly();
        }

        @Override
        public Decoder open(DecoderRequest request) {
            seen.add(request);
            return new Fake(name(), 0, CodecId.AV1, true).open(request);
        }
    }

    @Test
    @DisplayName("hands the player's hardware setting to every provider, in supports and in open")
    void handsTheSettingOn() {
        try (var container = av1()) {
            var seen = new ArrayList<DecoderRequest>();
            var provider = new HardwareOnly(seen);
            assertTrue(Decoders.supports(container, 0, List.of(provider), AUTO));
            assertEquals(
                    "hardware-only",
                    Decoders.open(container, 0, List.of(provider), 0, AUTO).provider());
            assertTrue(seen.stream().allMatch(request -> request.hardware() == AUTO), seen.toString());
            seen.clear();
            // Off: the provider says no, and without it nothing but the built-in
            // decoder is left.
            assertFalse(provider.supports(container.request(0).withHardware(OFF)));
            MediaNativesRequirement.enforce();
            var resolved = Decoders.open(container, 0, List.of(provider), 0, OFF);
            assertEquals(BuiltInDecoders.DAV1D, resolved.provider(), "the software decoder takes over");
            resolved.decoder().close();
            assertTrue(seen.stream().allMatch(DecoderRequest::softwareOnly), seen.toString());
        }
    }
}
