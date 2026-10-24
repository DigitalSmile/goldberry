package dev.goldberry.media.ffm;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// The layout probe's output for linux-x64, committed, so that the struct table
/// is checked on every machine and not only on one that built the library.
final class LayoutFixture {

    static final String RESOURCE = "media-layout-linux-x64.properties";

    private LayoutFixture() {}

    static NativeLayout load() {
        var in = Objects.requireNonNull(LayoutFixture.class.getResourceAsStream(RESOURCE), RESOURCE);
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return NativeLayout.parse(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
