package io.github.digitalsmile.goldberry.media.ffi;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;

/// The hardware half of one [FfmpegDecoder] (`docs/goldberry-media.md` §3,
/// "Video decode"; ADR-0470): the device, the `get_format` upcall that asks for
/// it, and copy-back.
///
/// ## Choosing
///
/// [#choose] looks through every decoder this build has for the codec, not only
/// the one `avcodec_find_decoder` prefers. For AV1 that one is `libdav1d`, which
/// has no hardware path, while FFmpeg's own `av1` decoder is hardware only. It
/// takes the first decoder with a configuration of the `HW_DEVICE_CTX` kind for
/// one of the [Hardware]'s device types, in the order they are listed.
///
/// ## Negotiating
///
/// The decoder offers its pixel formats to `get_format` once it knows the
/// stream, with the device's surface format first. The upcall takes it when it
/// is offered. When it is not, because FFmpeg could not start the device for this
/// stream and has offered again without it, the upcall hands the list to
/// `avcodec_default_get_format`, which picks software. So VP9 on a device with no
/// VP9 engine decodes in software inside the same decoder, and nothing fails. A
/// hardware-only decoder is offered nothing else, fails, and the Engine's
/// fallback ladder takes over.
///
/// The upcall never throws into C. Anything that goes wrong in it answers
/// `AV_PIX_FMT_NONE`, which fails the decode, and that is a fallback too.
///
/// ## Copy-back
///
/// A frame in the surface format is copied into a frame of this object's own
/// with `av_hwframe_transfer_data`, in the device's software format (NV12, or
/// P010 for 10-bit), with the timestamps and colour copied over. That frame is
/// lent out as any software frame is, and is valid until the next copy.
///
/// ## Lifetime
///
/// The device reference passes to the codec context in [#attachTo], and the
/// context unreferences it when it is freed. The stub and the software frame are
/// this object's, and [#close()] frees them. It must run after the context is
/// freed, because a context that calls a freed stub crashes the process.
final class HardwareDecoder implements AutoCloseable {

    private static final Logger LOG = Logs.of(HardwareDecoder.class);

    /// `enum AVPixelFormat (*get_format)(struct AVCodecContext *s, const enum AVPixelFormat *fmt)`
    static final FunctionDescriptor GET_FORMAT = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS);

    /// The longest list of pixel formats a decoder offers that is read. FFmpeg's
    /// lists are a handful of entries; this is a bound against a missing
    /// terminator, not a limit anyone reaches.
    private static final int MAX_OFFERED = 64;

    private static final MethodHandle GET_FORMAT_HANDLE;

    static {
        try {
            GET_FORMAT_HANDLE =
                    MethodHandles.lookup().findVirtual(HardwareDecoder.class, "getFormat", GET_FORMAT.toMethodType());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// A decoder and a way to decode on a device, not yet opened.
    ///
    /// @param codec       the `AVCodec*` that decodes on the device
    /// @param codecId     the codec it decodes
    /// @param pixelFormat the surface format its hardware frames come in
    /// @param deviceType  the `AVHWDeviceType`
    /// @param deviceName  FFmpeg's name for it, such as `videotoolbox`
    record Choice(MemorySegment codec, int codecId, int pixelFormat, int deviceType, String deviceName) {}

    private final Ffmpeg ffmpeg;
    private final Hardware hardware;
    private final Choice choice;
    private final Arena arena = Arena.ofShared();
    /// The `AVBufferRef*` of the device until [#attachTo] hands it to a context.
    private MemorySegment device;
    private final MemorySegment software;
    /// Whether the last `get_format` took the device's format.
    private volatile boolean negotiated;
    private boolean closed;

    private HardwareDecoder(Ffmpeg ffmpeg, Hardware hardware, Choice choice, MemorySegment device) {
        this.ffmpeg = ffmpeg;
        this.hardware = hardware;
        this.choice = choice;
        this.device = device;
        var frame = ffmpeg.util().frameAlloc().call();
        if (frame.equals(MemorySegment.NULL)) {
            Pointers.freeThrough(device, ffmpeg.util().bufferUnref()::call);
            throw new OutOfMemoryError("av_frame_alloc failed");
        }
        this.software = AvFrameView.of(frame);
    }

    /// The first decoder of this build that can decode codec `codecId` on one of
    /// `hardware`'s device types, trying the types in order. Nothing is opened.
    ///
    /// @param skipFailed whether to pass over a type that has failed for the codec
    ///                   before: yes when opening, and no when counting the
    ///                   ladder's rungs, which must not move between two calls
    /// @return the choice, or null when there is none: hardware off, a codec with
    ///         no hardware path in this build, or (with `skipFailed`) one that has
    ///         failed
    static @Nullable Choice choose(Ffmpeg ffmpeg, int codecId, Hardware hardware, boolean skipFailed) {
        for (var name : hardware.deviceTypes()) {
            if (skipFailed && hardware.failed(codecId, name)) {
                continue;
            }
            var type = deviceType(ffmpeg, name);
            if (type == ffmpeg.constants().video().hwDeviceTypeNone()) {
                continue;
            }
            var found = decoderFor(ffmpeg, codecId, type);
            if (found != null) {
                return new Choice(found.codec(), codecId, found.pixelFormat(), type, name);
            }
        }
        return null;
    }

    /// Opens the device `choice` names.
    ///
    /// @return the hardware half of a decoder, or null when the device would not
    ///         open, which is recorded in `hardware` so the next decoder does not
    ///         try it again
    static @Nullable HardwareDecoder open(Ffmpeg ffmpeg, Hardware hardware, Choice choice) {
        try (var scratch = Arena.ofConfined()) {
            var holder = scratch.allocate(ADDRESS);
            var result = hardware.calls().createDevice(ffmpeg, holder, choice.deviceType());
            var device = holder.get(ADDRESS, 0);
            if (result < 0 || device.equals(MemorySegment.NULL)) {
                LOG.info(
                        "no {} device for {} ({}); decoding in software",
                        choice.deviceName(),
                        ffmpeg.codecName(choice.codecId()),
                        ffmpeg.describe(result));
                hardware.markFailed(choice.codecId(), choice.deviceName());
                return null;
            }
            return new HardwareDecoder(ffmpeg, hardware, choice, device);
        }
    }

    /// The decoder to open the codec context from.
    MemorySegment codec() {
        return choice.codec();
    }

    /// FFmpeg's name for the device type.
    String deviceName() {
        return choice.deviceName();
    }

    /// Hands the device to `context` and points its `get_format` at this object.
    /// The context owns the device from here on.
    @SuppressWarnings("restricted")
    void attachTo(MemorySegment context) {
        var stub = Linker.nativeLinker().upcallStub(GET_FORMAT_HANDLE.bindTo(this), GET_FORMAT, arena);
        AvCodecContextView.getFormat(context, stub);
        AvCodecContextView.hwDeviceContext(context, device);
        device = MemorySegment.NULL;
    }

    /// Whether `frame` is a surface on the device, to be copied back.
    boolean isHardwareFrame(MemorySegment frame) {
        return AvFrameView.format(frame) == choice.pixelFormat();
    }

    /// Whether the decoder took the device's format the last time it chose one.
    boolean negotiated() {
        return negotiated;
    }

    /// Records that the device could not decode this codec, for every decoder
    /// that comes after this one.
    void markFailed() {
        hardware.markFailed(choice.codecId(), choice.deviceName());
    }

    /// Copies the surface `frame` into system memory, with its timestamps and
    /// colour tags.
    ///
    /// @return the software frame, valid until the next call
    /// @throws FfmpegException when the copy fails: not a [io.github.digitalsmile.goldberry.media.MediaException],
    ///                         so the Engine falls back to software rather than
    ///                         failing the playback
    MemorySegment copyBack(MemorySegment frame) {
        ffmpeg.util().frameUnref().call(software);
        var result = hardware.calls().transfer(ffmpeg, software, frame);
        if (result < 0) {
            throw new FfmpegException("av_hwframe_transfer_data", result, ffmpeg.describe(result));
        }
        ffmpeg.check("av_frame_copy_props", ffmpeg.util().frameCopyProps().call(software, frame));
        return software;
    }

    /// The `get_format` upcall: the device's format when it is offered, and
    /// FFmpeg's default choice when it is not.
    @SuppressWarnings({"restricted", "unused"})
    int getFormat(MemorySegment context, MemorySegment formats) {
        try {
            var offered = formats.reinterpret((long) MAX_OFFERED * JAVA_INT.byteSize());
            var none = ffmpeg.constants().video().pixFmtNone();
            var count = 0;
            for (; count < MAX_OFFERED; count++) {
                var format = offered.getAtIndex(JAVA_INT, count);
                if (format == none) {
                    break;
                }
                if (format == choice.pixelFormat()) {
                    negotiated = true;
                    return format;
                }
            }
            negotiated = false;
            // FFmpeg's default reads the last entry, so an empty list is not
            // handed to it: nothing to choose from is NONE.
            return count == 0 ? none : ffmpeg.codec().defaultGetFormat().call(context, formats);
        } catch (Throwable t) {
            // Nothing may be thrown into C. NONE fails the decode, which falls back.
            negotiated = false;
            return ffmpeg.constants().video().pixFmtNone();
        }
    }

    /// Frees the software frame, the stub, and a device no context took. After the
    /// codec context has been freed.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Pointers.freeThrough(software, ffmpeg.util().frameFree()::call);
        Pointers.freeThrough(device, ffmpeg.util().bufferUnref()::call);
        device = MemorySegment.NULL;
        arena.close();
    }

    private static int deviceType(Ffmpeg ffmpeg, String name) {
        try (var scratch = Arena.ofConfined()) {
            return ffmpeg.util().hwDeviceFindTypeByName().call(scratch.allocateFrom(name));
        }
    }

    /// A decoder and the surface format of its configuration for `deviceType`.
    private record Found(MemorySegment codec, int pixelFormat) {}

    private static @Nullable Found decoderFor(Ffmpeg ffmpeg, int codecId, int deviceType) {
        var method = ffmpeg.constants().video().hwConfigMethodHwDeviceCtx();
        try (var scratch = Arena.ofConfined()) {
            var opaque = scratch.allocate(ADDRESS);
            for (var codec = ffmpeg.codec().codecIterate().call(opaque);
                    !codec.equals(MemorySegment.NULL);
                    codec = ffmpeg.codec().codecIterate().call(opaque)) {
                if (AvCodecView.id(codec) != codecId
                        || ffmpeg.codec().isDecoder().call(codec) == 0) {
                    continue;
                }
                for (var index = 0; ; index++) {
                    var config = ffmpeg.codec().getHwConfig().call(codec, index);
                    if (config.equals(MemorySegment.NULL)) {
                        break;
                    }
                    var view = AvCodecHwConfigView.of(config);
                    if ((AvCodecHwConfigView.methods(view) & method) != 0
                            && AvCodecHwConfigView.deviceType(view) == deviceType) {
                        return new Found(codec, AvCodecHwConfigView.pixelFormat(view));
                    }
                }
            }
        }
        return null;
    }
}
