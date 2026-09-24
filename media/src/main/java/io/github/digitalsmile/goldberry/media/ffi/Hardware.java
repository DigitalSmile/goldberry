package io.github.digitalsmile.goldberry.media.ffi;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.media.HardwareDecoding;

/// How the built-in decoder uses hardware (phase 5, ADR-0470): which device types
/// it tries, in order, and what it has learned does not work.
///
/// A [HardwareDecoding] choice becomes one of these through [#of]. [#OFF] tries
/// nothing. [#AUTO] tries the platform's own device type, and is one object per
/// process, so what one playback learns the next does not have to learn again.
///
/// ## Learning
///
/// A device can open and still not decode a codec. VideoToolbox on an M1 has no
/// AV1 engine, and a GPU may lack VP9 profile 2. [#failed] records a codec and
/// device type that failed before giving a picture, and later decoders skip them.
/// The record lasts as long as the process does. A driver update needs a restart
/// anyway.
///
/// ## Faults
///
/// The two FFmpeg calls that fail on a real machine, opening the device and
/// copying a picture back, go through [Calls]. A test hands in its own to inject
/// the failures of scenario S4.
public final class Hardware {

    /// The FFmpeg calls hardware decode makes that fail on real machines, so that
    /// a test can make them fail on purpose.
    public interface Calls {

        /// `av_hwdevice_ctx_create(holder, type, NULL, NULL, 0)`.
        int createDevice(Ffmpeg ffmpeg, MemorySegment holder, int type);

        /// `av_hwframe_transfer_data(destination, source, 0)`: copy-back.
        int transfer(Ffmpeg ffmpeg, MemorySegment destination, MemorySegment source);

        /// FFmpeg's own.
        Calls FFMPEG = new Calls() {
            @Override
            public int createDevice(Ffmpeg ffmpeg, MemorySegment holder, int type) {
                return ffmpeg.util().hwDeviceCtxCreate().call(holder, type, MemorySegment.NULL, MemorySegment.NULL, 0);
            }

            @Override
            public int transfer(Ffmpeg ffmpeg, MemorySegment destination, MemorySegment source) {
                return ffmpeg.util().hwFrameTransferData().call(destination, source, 0);
            }
        };
    }

    /// Software only.
    public static final Hardware OFF = new Hardware(List.of(), Calls.FFMPEG);

    /// The platform's device types, shared by every player in the process.
    private static final Hardware AUTO = new Hardware(deviceTypes(platformOrNull()), Calls.FFMPEG);

    private final List<String> deviceTypes;
    private final Calls calls;
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    /// A policy that tries `deviceTypes`, FFmpeg's names for them, in order,
    /// through `calls`. For a test; an application says [HardwareDecoding].
    public Hardware(List<String> deviceTypes, Calls calls) {
        this.deviceTypes = List.copyOf(deviceTypes);
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /// The policy for `mode`.
    public static Hardware of(HardwareDecoding mode) {
        return switch (mode) {
            case AUTO -> AUTO;
            case OFF -> OFF;
        };
    }

    /// FFmpeg's names for the device types `platform` decodes on, in the order
    /// they are tried: the one the OS itself provides.
    static List<String> deviceTypes(@Nullable FfmpegPlatform platform) {
        if (platform == null) {
            return List.of();
        }
        return switch (platform.os()) {
            case MACOS -> List.of("videotoolbox");
            case WINDOWS -> List.of("d3d11va");
            case LINUX -> List.of("vaapi");
        };
    }

    private static @Nullable FfmpegPlatform platformOrNull() {
        try {
            return FfmpegPlatform.current();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /// The device types to try, in order. Empty for software only.
    public List<String> deviceTypes() {
        return deviceTypes;
    }

    /// Whether any device type is tried at all.
    public boolean enabled() {
        return !deviceTypes.isEmpty();
    }

    Calls calls() {
        return calls;
    }

    /// Whether `deviceType` has already failed to decode codec `codecId` in this
    /// process.
    boolean failed(int codecId, String deviceType) {
        return failed.contains(key(codecId, deviceType));
    }

    /// Records that `deviceType` could not decode codec `codecId`, so that the
    /// next decoder goes to software at once.
    void markFailed(int codecId, String deviceType) {
        failed.add(key(codecId, deviceType));
    }

    private static String key(int codecId, String deviceType) {
        return codecId + "/" + deviceType;
    }

    @Override
    public String toString() {
        return enabled() ? "Hardware" + deviceTypes : "Hardware[off]";
    }
}
