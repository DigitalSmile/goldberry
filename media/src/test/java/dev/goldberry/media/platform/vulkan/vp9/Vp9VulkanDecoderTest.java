package dev.goldberry.media.platform.vulkan.vp9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.bitstream.vp9.ColorConfig;
import dev.goldberry.media.codec.CodecId;
import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Rational;
import dev.goldberry.media.codec.TrackParams;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.platform.vulkan.VulkanCodec;

/// What a VP9 track claims of the device, and the matrix a frame's colour
/// space names.
@DisplayName("VP9 on Vulkan Video")
class Vp9VulkanDecoderTest {

    private static DecoderRequest request(Optional<String> pixelFormat, OptionalInt profile) {
        var video = new TrackParams.Video(
                256,
                144,
                pixelFormat,
                profile,
                OptionalInt.empty(),
                OptionalLong.empty(),
                Optional.empty(),
                false,
                ColorDescription.UNSPECIFIED);
        return new DecoderRequest(CodecId.VP9, "vp9", video, MemorySegment.NULL, new Rational(1, 1000));
    }

    @ParameterizedTest(name = "{0}, profile {1}: profile {2}, {3} bits")
    @CsvSource({"yuv420p, , 0, 8", "yuv420p10le, , 2, 10", ", 2, 2, 10", ", , 0, 8"})
    @DisplayName("the profile and depth come from the pixel format, else the profile")
    void claims(String pixelFormat, Integer profile, int expectedProfile, int depth) {
        var claim = Vp9VulkanDecoder.claim(request(
                        Optional.ofNullable(pixelFormat),
                        profile == null ? OptionalInt.empty() : OptionalInt.of(profile)))
                .orElseThrow();
        assertEquals(VulkanCodec.VP9, claim.profile().codec());
        assertEquals(expectedProfile, claim.profile().profileIdc());
        assertEquals(depth, claim.profile().bitDepth());
        assertEquals(256, claim.width());
        assertEquals(144, claim.height());
    }

    @Test
    @DisplayName("4:4:4, 12 bits and profiles 1 and 3 are not claimed")
    void refuses() {
        assertTrue(Vp9VulkanDecoder.claim(request(Optional.of("yuv444p"), OptionalInt.empty()))
                .isEmpty());
        assertTrue(Vp9VulkanDecoder.claim(request(Optional.of("yuv420p12le"), OptionalInt.empty()))
                .isEmpty());
        assertTrue(Vp9VulkanDecoder.claim(request(Optional.empty(), OptionalInt.of(1)))
                .isEmpty());
        assertTrue(Vp9VulkanDecoder.claim(request(Optional.empty(), OptionalInt.of(3)))
                .isEmpty());
    }

    @Test
    @DisplayName("the colour space names the matrix as the built-in decoder reads it")
    void matrices() {
        assertEquals(VideoFrame.ColorMatrix.BT709, Vp9VulkanDecoder.matrix(ColorConfig.CS_BT_709, 144));
        assertEquals(VideoFrame.ColorMatrix.BT2020, Vp9VulkanDecoder.matrix(ColorConfig.CS_BT_2020, 144));
        assertEquals(VideoFrame.ColorMatrix.BT601, Vp9VulkanDecoder.matrix(ColorConfig.CS_SMPTE_170, 1080));
        assertEquals(VideoFrame.ColorMatrix.BT601, Vp9VulkanDecoder.matrix(ColorConfig.CS_UNKNOWN, 144));
        assertEquals(VideoFrame.ColorMatrix.BT709, Vp9VulkanDecoder.matrix(ColorConfig.CS_UNKNOWN, 720));
    }
}
