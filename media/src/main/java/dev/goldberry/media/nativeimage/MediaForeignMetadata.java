package dev.goldberry.media.nativeimage;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

import dev.goldberry.media.ffi.FfmpegDescriptors;

/// Writes this module's `reachability-metadata.json`: every foreign-call shape a
/// native image of it needs, and the natives jar's resources (ADR-0339).
///
/// Two sources of shapes, FFmpeg's bindings ([FfmpegDescriptors]) and the system
/// decoders' ([PlatformDescriptors]), in one file, each shape once. The
/// resources entry is FFmpeg's: the libraries inside `goldberry-media`'s
/// `ffmpeg-<target>` jars are read with `getResourceAsStream`, and an image
/// includes only resources it is told about. The system decoders ship no
/// native code, and their service files are found by the image's own
/// `ServiceLoader` support.
///
/// The class is package-private and so is its `main`: JDK 25's launcher
/// (JEP 512) runs it as it is, and `:media:foreignMetadata` is its only caller.
final class MediaForeignMetadata {

    /// Where `FfmpegLibraries` finds the libraries of a natives jar.
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

    /// FFmpeg's downcalls, then the system decoders', each shape once.
    static List<FunctionDescriptor> downcalls() {
        return union(FfmpegDescriptors.downcalls(), PlatformDescriptors.downcalls());
    }

    /// FFmpeg's upcalls, then the system decoders', each shape once.
    static List<FunctionDescriptor> upcalls() {
        return union(FfmpegDescriptors.UPCALLS, PlatformDescriptors.UPCALLS);
    }

    private static List<FunctionDescriptor> union(List<FunctionDescriptor> first, List<FunctionDescriptor> second) {
        var all = new LinkedHashSet<FunctionDescriptor>(first);
        all.addAll(second);
        return List.copyOf(all);
    }
}
