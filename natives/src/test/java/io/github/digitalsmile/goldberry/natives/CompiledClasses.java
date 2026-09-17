package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Where this module's compiled classes are, found through a class rather than
/// through a path, so it works from Gradle and from an IDE alike.
///
/// Through the code source's **URI**, never its `getPath()`. On Windows the
/// location is `file:/D:/a/goldberry/goldberry/natives/build/classes/java/test/`
/// and `getPath()` hands back `/D:/a/...` with the leading slash kept, which
/// `Path.of(String)` refuses with `Illegal char <:> at index 3`. `Path.of(URI)` is
/// the conversion that knows about drive letters, and it is the same call on
/// every platform. Two tests each carried their own copy of the `getPath()` form
/// and both failed on the first Windows run that reached them (ADR-0338).
final class CompiledClasses {

    private CompiledClasses() {}

    /// `.../build/classes/java/test` -> `.../build/classes/java/main`, for the
    /// module `witness` was compiled into.
    static Path mainClassesBeside(Class<?> witness) {
        var main = testClassesOf(witness).resolveSibling("main");
        if (!Files.isDirectory(main)) {
            fail("expected the main classes beside the test classes, at " + main);
        }
        return main;
    }

    /// The directory `witness` was loaded from.
    static Path testClassesOf(Class<?> witness) {
        var source = witness.getProtectionDomain().getCodeSource();
        if (source == null) {
            fail("no code source for " + witness.getName() + "; cannot locate the module's own classes");
        }
        try {
            return Path.of(source.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new AssertionError(
                    "the code source of " + witness.getName() + " is not a file URI: " + source.getLocation(), e);
        }
    }
}
