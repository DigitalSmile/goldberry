package dev.goldberry.media.nativeimage;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

import dev.goldberry.media.ffm.DowncallShape;
import dev.goldberry.media.ffm.MediaDescriptors;

/// Writes this module's `reachability-metadata.json`: every foreign-call shape a
/// native image of it needs, and the natives jar's resources. A foreign call is
/// registered because it exists, not because a traced run reached it.
///
/// Two sources of shapes, the `libgoldberry-media` bindings ([MediaDescriptors])
/// and the system decoders' ([PlatformDescriptors]), in one file, each shape
/// once. The resources entry is the natives jar's: `MediaNatives` reads the
/// library and its layout file out of `goldberry-media`'s `natives-<target>`
/// jar with `getResourceAsStream`, and an image includes only resources it is
/// told about. The system decoders ship no native code, and their service
/// files are found by the image's own `ServiceLoader` support.
///
/// The class is package-private and so is its `main`: JDK 25's launcher
/// (JEP 512) runs it as it is, and `:media:foreignMetadata` is its only caller.
final class MediaForeignMetadata {

    /// Where `MediaNatives` finds the library and its layout file in a natives
    /// jar: `dev/goldberry/media/natives/<target>/`.
    static final String NATIVES_GLOB = "dev/goldberry/media/natives/**";

    private MediaForeignMetadata() {}

    /// Writes the file named by the one argument.
    static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: MediaForeignMetadata <reachability-metadata.json>");
        }
        var target = Path.of(args[0]).toAbsolutePath();
        var directory = target.getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Files.writeString(target, render());
    }

    /// The whole file.
    static String render() {
        return MetadataGrammar.render(List.of(NATIVES_GLOB), downcalls(), upcalls());
    }

    /// The media bindings' downcalls, then the system decoders', each shape once.
    /// The system decoders call nothing variadic.
    static List<DowncallShape> downcalls() {
        return union(
                MediaDescriptors.downcalls(),
                PlatformDescriptors.downcalls().stream().map(DowncallShape::of).toList());
    }

    /// The media bindings' upcalls, then the system decoders', each shape once.
    static List<FunctionDescriptor> upcalls() {
        return union(MediaDescriptors.UPCALLS, PlatformDescriptors.UPCALLS);
    }

    private static <T> List<T> union(List<T> first, List<T> second) {
        var all = new LinkedHashSet<T>(first);
        all.addAll(second);
        return List.copyOf(all);
    }
}
