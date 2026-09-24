package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.MemorySegment;

/// The fields of `AVCodecHWConfig` ([FfmpegStructs#AV_CODEC_HW_CONFIG]): one way
/// a decoder can decode on a device, as `avcodec_get_hw_config` lists them.
final class AvCodecHwConfigView {

    private static final long PIX_FMT = FfmpegStructs.AV_CODEC_HW_CONFIG.byteOffset(groupElement("pix_fmt"));
    private static final long METHODS = FfmpegStructs.AV_CODEC_HW_CONFIG.byteOffset(groupElement("methods"));
    private static final long DEVICE_TYPE = FfmpegStructs.AV_CODEC_HW_CONFIG.byteOffset(groupElement("device_type"));

    private AvCodecHwConfigView() {}

    static MemorySegment of(MemorySegment config) {
        return Pointers.struct(config, FfmpegStructs.AV_CODEC_HW_CONFIG);
    }

    /// The pixel format a frame decoded this way comes in: a surface, such as
    /// `videotoolbox_vld`, and not pixels.
    static int pixelFormat(MemorySegment config) {
        return config.get(JAVA_INT, PIX_FMT);
    }

    /// `AV_CODEC_HW_CONFIG_METHOD_*` bits: how the device is handed over.
    static int methods(MemorySegment config) {
        return config.get(JAVA_INT, METHODS);
    }

    /// `AVHWDeviceType`.
    static int deviceType(MemorySegment config) {
        return config.get(JAVA_INT, DEVICE_TYPE);
    }
}
