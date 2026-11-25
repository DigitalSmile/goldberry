package dev.goldberry.vector.nativeimage;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.goldberry.vector.thorvg.VectorDescriptors;

/// Writes this module's `reachability-metadata.json`: every foreign-call shape
/// the ThorVG bindings link, and the natives jar's resources. A foreign call is
/// registered because it exists, not because a traced run reached it.
///
/// The resources entry is the natives jar's: `VectorNatives` reads the library
/// and `vector.properties` out of `goldberry-vector`'s `natives-<target>` jar
/// with `getResourceAsStream`, and an image includes only resources it is told
/// about.
///
/// Package-private, and so is `main`: JDK 25's launcher (JEP 512) runs it as it
/// is, and `:vector:foreignMetadata` is its only caller.
final class VectorForeignMetadata {

    /// Where `VectorNatives` finds the library in a natives jar:
    /// `dev/goldberry/vector/natives/<target>/`.
    static final String NATIVES_GLOB = "dev/goldberry/vector/natives/**";

    private VectorForeignMetadata() {}

    /// Writes the file named by the one argument.
    static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: VectorForeignMetadata <reachability-metadata.json>");
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
        return MetadataGrammar.render(List.of(NATIVES_GLOB), downcalls());
    }

    /// Every downcall the bindings link, each once.
    static List<FunctionDescriptor> downcalls() {
        return VectorDescriptors.downcalls();
    }
}
