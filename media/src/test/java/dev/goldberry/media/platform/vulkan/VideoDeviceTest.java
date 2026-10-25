package dev.goldberry.media.platform.vulkan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The Vulkan video device on this machine, where there is one: what it
/// decodes, printed for whoever reads the test's output.
@DisplayName("the Vulkan video device")
class VideoDeviceTest {

    @Test
    @DisplayName("is made where the system has Vulkan Video, and answers for each codec it decodes")
    void capabilities() {
        var device = VideoDevice.get();
        Assumptions.assumeTrue(device.isPresent(), "no Vulkan Video on this machine");
        System.out.println(device.get());
        var any = false;
        for (var profile : new VideoProfile[] {
            new VideoProfile(VulkanCodec.H264, 100, 8),
            new VideoProfile(VulkanCodec.HEVC, 1, 8),
            new VideoProfile(VulkanCodec.HEVC, 2, 10),
            new VideoProfile(VulkanCodec.VP9, 0, 8),
            new VideoProfile(VulkanCodec.VP9, 2, 10),
            new VideoProfile(VulkanCodec.AV1, 0, 8),
            new VideoProfile(VulkanCodec.AV1, 0, 10)
        }) {
            var capabilities = VideoCapabilities.query(device.get(), profile);
            System.out.println(
                    profile + ": " + capabilities.map(Object::toString).orElse("not decoded"));
            any |= capabilities.isPresent();
        }
        assertTrue(any, "a device that decodes decodes something");
    }
}
