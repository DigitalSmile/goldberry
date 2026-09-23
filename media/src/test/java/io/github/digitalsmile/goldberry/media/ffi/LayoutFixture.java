package io.github.digitalsmile.goldberry.media.ffi;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// The layout probe's output for FFmpeg `n8.1.3` on macos-aarch64, committed as a
/// test resource.
///
/// It lets the layouts be checked on every machine, with no FFmpeg built. The
/// superbuild regenerates the real file per target and the native tests check
/// that one too. Refresh this copy when the pin moves: build the probe against the
/// new headers and write its output over the resource.
final class LayoutFixture {

    static final String RESOURCE = "ffmpeg-layout-macos-aarch64.properties";

    private LayoutFixture() {}

    static FfmpegLayout load() {
        var in = Objects.requireNonNull(LayoutFixture.class.getResourceAsStream(RESOURCE), RESOURCE);
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return FfmpegLayout.parse(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FfmpegConstants constants() {
        return FfmpegConstants.from(load());
    }
}
