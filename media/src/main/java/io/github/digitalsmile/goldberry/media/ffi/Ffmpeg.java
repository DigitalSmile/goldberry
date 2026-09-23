package io.github.digitalsmile.goldberry.media.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import io.github.digitalsmile.goldberry.media.ffi.calls.AvCodecCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.AvFormatCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.AvUtilCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.SwResampleCalls;
import io.github.digitalsmile.goldberry.media.ffi.calls.SwScaleCalls;

/// FFmpeg, loaded and checked: the bound functions of all five libraries, the
/// constants the probe reported, and where they came from.
///
/// Only [FfmpegLibraries] makes one, and only after the majors and the struct
/// layouts have been checked. So a caller holding an `Ffmpeg` may touch the
/// structs in [FfmpegStructs].
public final class Ffmpeg {

    private final AvUtilCalls util;
    private final AvFormatCalls format;
    private final AvCodecCalls codec;
    private final SwResampleCalls swResample;
    private final SwScaleCalls swScale;
    private final FfmpegConstants constants;
    private final Path directory;

    Ffmpeg(
            AvUtilCalls util,
            AvFormatCalls format,
            AvCodecCalls codec,
            SwResampleCalls swResample,
            SwScaleCalls swScale,
            FfmpegConstants constants,
            Path directory) {
        this.util = Objects.requireNonNull(util, "util");
        this.format = Objects.requireNonNull(format, "format");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.swResample = Objects.requireNonNull(swResample, "swResample");
        this.swScale = Objects.requireNonNull(swScale, "swScale");
        this.constants = Objects.requireNonNull(constants, "constants");
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    /// `libavutil`'s functions.
    public AvUtilCalls util() {
        return util;
    }

    /// `libavformat`'s functions.
    public AvFormatCalls format() {
        return format;
    }

    /// `libavcodec`'s functions.
    public AvCodecCalls codec() {
        return codec;
    }

    /// `libswresample`'s functions.
    public SwResampleCalls swResample() {
        return swResample;
    }

    /// `libswscale`'s functions.
    public SwScaleCalls swScale() {
        return swScale;
    }

    /// The constants, as the probe reported them for this build.
    public FfmpegConstants constants() {
        return constants;
    }

    /// The directory the libraries were loaded from.
    public Path directory() {
        return directory;
    }

    /// `av_strerror`'s text for `code`.
    public String describe(int code) {
        try (var arena = Arena.ofConfined()) {
            var buffer = arena.allocate(256);
            util.strError().call(code, buffer, buffer.byteSize());
            return buffer.getString(0);
        }
    }

    /// Throws an [FfmpegException] when `result` is a negative `AVERROR`, and
    /// returns it otherwise.
    public int check(String function, int result) {
        if (result < 0) {
            throw new FfmpegException(function, result, describe(result));
        }
        return result;
    }

    /// FFmpeg's name for a codec id, as `CodecId` maps it.
    public String codecName(int codecId) {
        return Objects.requireNonNullElse(Pointers.string(codec.getName().call(codecId)), "unknown_codec");
    }

    /// FFmpeg's name for a pixel format, or empty for an unknown one.
    public Optional<String> pixelFormatName(int pixelFormat) {
        return Optional.ofNullable(Pointers.string(util.pixFmtName().call(pixelFormat)));
    }

    /// FFmpeg's name for a sample format, or empty for an unknown one.
    public Optional<String> sampleFormatName(int sampleFormat) {
        return Optional.ofNullable(Pointers.string(util.sampleFmtName().call(sampleFormat)));
    }

    /// Allocates `size` bytes with `av_malloc`, for a buffer FFmpeg may free or
    /// replace itself.
    ///
    /// @throws OutOfMemoryError when FFmpeg's allocator fails
    public MemorySegment malloc(long size) {
        var block = util.malloc().call(size);
        if (block.equals(MemorySegment.NULL)) {
            throw new OutOfMemoryError("av_malloc(" + size + ") failed");
        }
        return block;
    }

    /// Frees what [#malloc] returned. A null pointer is ignored, as `av_free`
    /// ignores it.
    public void free(MemorySegment block) {
        util.free().call(block);
    }
}
