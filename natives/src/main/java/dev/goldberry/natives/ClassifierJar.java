package dev.goldberry.natives;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.FindException;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// Where a library packaged in a `goldberry-natives-<classifier>` jar is read
/// from, wherever the application put that jar.
///
/// ```java
/// try (var in = ClassifierJar.open(NativePlatform.current(), "/dev/goldberry/natives/linux-x64/libgoldberry.so")) {
///     // ...
/// }
/// ```
///
/// Three places, in order:
///
/// 1. **this module**, for a build that packaged the library into it;
/// 2. **the system class loader**, which finds a jar on the class path, and one
///    on the module path whose module was resolved;
/// 3. **the module path itself**, for the jar that is there and was not
///    resolved. Each classifier jar names itself, `dev.goldberry.natives.linux_x64`
///    and so on, so a modular build keeps it on the module path; nothing
///    `requires` a platform's name, though, so the module system leaves it out
///    of the boot layer unless an `--add-modules` says otherwise. It is still
///    on the path, and it is read from there.
///
/// The library sits in a directory that is not a package name, so no module
/// encapsulates it and nothing has to be opened.
///
/// Read more:
/// [Installing](https://goldberry.dev/docs/getting-started/installing.html#the-module-path).
public final class ClassifierJar {

    private ClassifierJar() {}

    /// Opens `resource` from the classifier jar for `platform`, or answers null
    /// when none of the three places has it.
    ///
    /// @param resource an absolute resource name, leading slash and all
    public static @Nullable InputStream open(NativePlatform platform, String resource) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(resource, "resource");
        if (!resource.startsWith("/")) {
            throw new IllegalArgumentException("a classifier resource is absolute, not " + resource);
        }
        var own = ClassifierJar.class.getResourceAsStream(resource);
        if (own != null) {
            return own;
        }
        // Without the leading slash: a ClassLoader resource name is always
        // absolute, and one that starts with `/` matches nothing — silently.
        var relative = resource.substring(1);
        var loaded = ClassLoader.getSystemResourceAsStream(relative);
        if (loaded != null) {
            return loaded;
        }
        return fromModulePath(System.getProperty("jdk.module.path"), platform.moduleName(), relative);
    }

    /// `resource` from the module called `module` on `modulePath`, or null.
    ///
    /// Package-private for the test, which hands it a module path of its own.
    static @Nullable InputStream fromModulePath(@Nullable String modulePath, String module, String resource) {
        if (modulePath == null || modulePath.isBlank()) {
            return null;
        }
        var entries = new ArrayList<Path>();
        for (var entry : Pattern.compile(Pattern.quote(File.pathSeparator)).split(modulePath)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                entries.add(Path.of(entry));
            } catch (InvalidPathException e) {
                // Not a path this platform can read, so not where the jar is.
            }
        }
        Optional<ModuleReference> found;
        try {
            found = ModuleFinder.of(entries.toArray(Path[]::new)).find(module);
        } catch (FindException e) {
            // A path the launcher accepted and this cannot read is not one to
            // fail the library over; the caller says what it looked for.
            return null;
        }
        if (found.isEmpty()) {
            return null;
        }
        try {
            var reader = found.get().open();
            var in = reader.open(resource);
            if (in.isEmpty()) {
                reader.close();
                return null;
            }
            return new ReaderStream(in.get(), reader);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + resource + " from module " + module, e);
        }
    }

    /// A stream that closes the jar it came out of when it is closed.
    private static final class ReaderStream extends FilterInputStream {

        private final ModuleReader reader;

        ReaderStream(InputStream in, ModuleReader reader) {
            super(in);
            this.reader = reader;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                reader.close();
            }
        }
    }
}
