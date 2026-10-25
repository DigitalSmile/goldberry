package dev.goldberry.media.platform.vulkan;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.platform.vulkan.av1.Av1VulkanDecoder;
import dev.goldberry.media.platform.vulkan.h264.H264VulkanDecoder;
import dev.goldberry.media.platform.vulkan.hevc.H265VulkanDecoder;
import dev.goldberry.media.platform.vulkan.vp9.Vp9VulkanDecoder;

/// H.264 and HEVC, and with [#PROPERTY] set to `all` VP9 and AV1 too, decoded
/// by the GPU's video engine through Vulkan Video.
///
/// Supports a video track when the system's Vulkan has a device whose queue
/// decodes the codec ([VideoDevice]), the track's configuration names a
/// profile and size the device decodes, and the player allows the video engine:
/// with [HardwareDecoding#OFF] this provider supports nothing, for there is no
/// software path through it. Its [#priority()] is below the system decoders',
/// so where the system decodes a codec this is the next rung, and the built-in
/// decoders come after.
///
/// VP9 and AV1 are left to the built-in decoders unless asked for: their
/// pictures are not checked on every machine's driver, and the built-in ones
/// are exact. `-Dgoldberry.media.vulkanVideo=off` turns the provider off.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public final class VulkanVideoProvider implements DecoderProvider {

    private static final Logger LOG = Logs.of(VulkanVideoProvider.class);

    /// This provider's [#name()].
    public static final String NAME = "vulkan-video";

    /// The system property that says which codecs are claimed: `off`, `auto`
    /// (H.264 and HEVC, the default) or `all`.
    public static final String PROPERTY = "goldberry.media.vulkanVideo";

    /// This provider's [#priority()]: below the system decoders, which answer
    /// the default, 0, and above `ffmpeg-cli`.
    public static final int PRIORITY = -10;

    /// Which codecs a property value claims.
    enum Policy {
        OFF,
        AUTO,
        ALL;

        /// Whether `codec` is claimed.
        boolean claims(VulkanCodec codec) {
            return switch (this) {
                case OFF -> false;
                case AUTO -> codec == VulkanCodec.H264 || codec == VulkanCodec.HEVC;
                case ALL -> true;
            };
        }

        /// The policy `value` names; [#AUTO] for none, and for one it does not
        /// know, which is logged.
        static Policy of(String value) {
            if (value.isBlank()) {
                return AUTO;
            }
            try {
                return valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException _) {
                LOG.warn("-D{}={} is none of off, auto, all; auto it is", PROPERTY, value);
                return AUTO;
            }
        }
    }

    private final Policy policy;

    /// A provider whose codecs [#PROPERTY] says. What `ServiceLoader` makes;
    /// nothing is loaded until a track is offered.
    public VulkanVideoProvider() {
        this(Policy.of(System.getProperty(PROPERTY, "")));
    }

    VulkanVideoProvider(Policy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean supports(DecoderRequest request) {
        var claim = claim(request);
        return claim.isPresent()
                && VideoDevice.get().map(claim.get()::decodesOn).orElse(false);
    }

    @Override
    public Decoder open(DecoderRequest request) {
        var claim = claim(request)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vulkan Video does not decode this " + request.codecName() + " track"));
        var device = VideoDevice.get().orElseThrow(() -> new IllegalStateException("no Vulkan device decodes video"));
        if (!claim.decodesOn(device)) {
            throw new IllegalArgumentException(device.name() + " does not decode " + claim);
        }
        var pictures = new HostPictures();
        try {
            var codec = switch (claim.profile().codec()) {
                case H264 -> new H264VulkanDecoder(device, request, pictures);
                case HEVC -> new H265VulkanDecoder(device, request, pictures);
                case VP9 -> new Vp9VulkanDecoder(device, pictures);
                case AV1 -> new Av1VulkanDecoder(device, request, pictures);
            };
            return new VulkanVideoDecoder(codec, pictures);
        } catch (RuntimeException e) {
            pictures.close();
            throw e;
        }
    }

    /// What the track needs of the device, when the policy and the player
    /// allow it and its configuration reads.
    Optional<VideoClaim> claim(DecoderRequest request) {
        if (request.hardware() == HardwareDecoding.OFF) {
            return Optional.empty();
        }
        return VulkanCodec.of(request.codec()).filter(policy::claims).flatMap(codec -> switch (codec) {
            case H264 -> H264VulkanDecoder.claim(request);
            case HEVC -> H265VulkanDecoder.claim(request);
            case VP9 -> Vp9VulkanDecoder.claim(request);
            case AV1 -> Av1VulkanDecoder.claim(request);
        });
    }
}
