package dev.goldberry.media.platform.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.ffmpegcli.FfmpegCliProvider;

/// The provider's policy: which codecs a property value claims, and where it
/// ranks.
@DisplayName("the Vulkan Video provider")
class VulkanVideoProviderTest {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({"'', AUTO", "off, OFF", "auto, AUTO", "ALL, ALL", " all , ALL", "sometimes, AUTO"})
    @DisplayName("the property names a policy, and anything else is auto")
    void policyOfProperty(String value, VulkanVideoProvider.Policy policy) {
        assertEquals(policy, VulkanVideoProvider.Policy.of(value));
    }

    @Test
    @DisplayName("auto claims H.264 and HEVC, all claims VP9 and AV1 too, off claims nothing")
    void policyCodecs() {
        var auto = VulkanVideoProvider.Policy.AUTO;
        assertTrue(auto.claims(VulkanCodec.H264));
        assertTrue(auto.claims(VulkanCodec.HEVC));
        assertFalse(auto.claims(VulkanCodec.VP9));
        assertFalse(auto.claims(VulkanCodec.AV1));
        for (var codec : VulkanCodec.values()) {
            assertTrue(VulkanVideoProvider.Policy.ALL.claims(codec));
            assertFalse(VulkanVideoProvider.Policy.OFF.claims(codec));
        }
    }

    @Test
    @DisplayName("ranks below the system decoders and above ffmpeg-cli")
    void priority() {
        var provider = new VulkanVideoProvider();
        assertTrue(provider.priority() < 0);
        assertTrue(provider.priority() > FfmpegCliProvider.PRIORITY);
        assertEquals("vulkan-video", provider.name());
    }
}
