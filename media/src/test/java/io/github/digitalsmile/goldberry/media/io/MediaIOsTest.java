package io.github.digitalsmile.goldberry.media.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("MediaIOs")
class MediaIOsTest {

    /// A MediaIO that is nothing, to tell which provider opened a source.
    record Marker(String name) implements MediaIO {
        @Override
        public int read(ByteBuffer target) {
            return -1;
        }

        @Override
        public void seek(long position) {}

        @Override
        public long position() {
            return 0;
        }

        @Override
        public OptionalLong size() {
            return OptionalLong.empty();
        }

        @Override
        public void close() {}
    }

    record Provider(Set<String> schemes, int priority, MediaIO io) implements MediaIOProvider {
        @Override
        public MediaIO open(Source source) {
            return io;
        }
    }

    @TempDir
    Path directory;

    @Test
    @DisplayName("opens file: with the built-in FileIO")
    void file() throws IOException {
        var path = Files.write(directory.resolve("a.bin"), new byte[] {7});
        try (var io = MediaIOs.open(Source.of(path), List.of())) {
            assertInstanceOf(FileIO.class, io);
            assertEquals(OptionalLong.of(1), io.size());
        }
    }

    @Test
    @DisplayName("refuses a scheme nothing opens, naming it")
    void unsupported() {
        var error = assertThrows(
                UnsupportedSchemeException.class,
                () -> MediaIOs.open(Source.of(URI.create("s3://bucket/clip.webm")), List.of()));
        assertEquals("s3", error.scheme());
    }

    @Test
    @DisplayName("asks a provider that claims the scheme, the highest priority first")
    void priority() throws IOException {
        var low = new Marker("low");
        var high = new Marker("high");
        var providers = List.of(new Provider(Set.of("s3"), 1, low), new Provider(Set.of("s3"), 5, high));
        assertSame(high, MediaIOs.open(Source.of(URI.create("s3://bucket/a")), providers));
    }

    @Test
    @DisplayName("lets a provider replace file:")
    void replacesFile() throws IOException {
        var sandboxed = new Marker("sandbox");
        var providers = List.of(new Provider(Set.of("file"), 0, sandboxed));
        assertSame(sandboxed, MediaIOs.open(Source.of(directory.resolve("x")), providers));
    }

    @Test
    @DisplayName("ignores a provider for another scheme")
    void otherScheme() {
        var providers = List.of(new Provider(Set.of("s3"), 0, new Marker("s3")));
        assertThrows(
                UnsupportedSchemeException.class,
                () -> MediaIOs.open(Source.of(URI.create("gs://bucket/a")), providers));
    }
}
