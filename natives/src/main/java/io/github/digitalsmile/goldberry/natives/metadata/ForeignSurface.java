package io.github.digitalsmile.goldberry.natives.metadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.FunctionDescriptor;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.Upcalls;
import io.github.digitalsmile.goldberry.natives.glib.GlibLog;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventWatch;
import io.github.digitalsmile.goldberry.natives.sdl.SdlFileDialogs;
import io.github.digitalsmile.goldberry.natives.sdl.SdlLog;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlClipboard;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlTray;
import io.github.digitalsmile.goldberry.natives.webview.Webview;
import io.github.digitalsmile.goldberry.natives.yoga.MeasureCallback;

/// Every foreign signature this module can cross, found from the classes rather
/// than from a run (ADR-0339).
///
/// A holder links its `FD_…` handle in its class initialiser, so initialising
/// every class in the `…calls` packages is what fills [Downcalls#linked()]; the
/// classes are found by listing the module, so a holder added tomorrow is
/// counted tomorrow. The upcall owners are a list, because an upcall's
/// descriptor lives beside the stub that uses it, and a test holds the list to
/// the source tree.
///
/// Initialising a holder needs no `libgoldberry`: an unbound handle is linked
/// from a descriptor and names no address, which is the whole reason a holder is
/// shaped as it is (ADR-0173).
public final class ForeignSurface {

    /// The packages that hold nothing but holders, which is what lets them be
    /// initialised wholesale here and at image build time alike.
    static final String HOLDER_PACKAGE_SUFFIX = ".calls";

    /// The classes that make an upcall stub, each declaring its shape through
    /// [Upcalls#describe] in a static initialiser.
    static final List<Class<?>> UPCALL_OWNERS = List.of(
            SdlEventWatch.class,
            SdlFileDialogs.class,
            SdlTray.class,
            MeasureCallback.class,
            SdlClipboard.class,
            // The two log bridges (ADR-0443). `GlibLog` declares two shapes
            // rather than one -- a `GLogFunc` and a `GLogWriterFunc` -- which is
            // the first owner here to do so, and the reason `describe` is a call
            // rather than a field.
            SdlLog.class,
            GlibLog.class,
            // A page's own script calling back (ADR-0448). One stub for every
            // binding of every page, so the owner is the wrapper rather than
            // anything per-page.
            Webview.class);

    private ForeignSurface() {}

    /// Every downcall descriptor, after initialising every holder.
    public static List<FunctionDescriptor> downcalls() {
        for (var name : holderClassNames()) {
            initialise(name);
        }
        return Downcalls.linked();
    }

    /// Every upcall descriptor, after initialising every owner.
    public static List<FunctionDescriptor> upcalls() {
        for (var owner : UPCALL_OWNERS) {
            initialise(owner.getName());
        }
        return Upcalls.declared();
    }

    /// The binary names of every class in a `…calls` package of this module,
    /// nested holders included, sorted.
    static List<String> holderClassNames() {
        try (Stream<String> resources = resourceNames()) {
            return resources
                    .filter(name -> name.endsWith(".class"))
                    .map(name ->
                            name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .filter(ForeignSurface::inHolderPackage)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean inHolderPackage(String binaryName) {
        var dot = binaryName.lastIndexOf('.');
        return dot > 0 && binaryName.substring(0, dot).endsWith(HOLDER_PACKAGE_SUFFIX);
    }

    /// The resource names of this module: through its reader when it is a named
    /// module, else by walking the code source, which is a directory under
    /// Gradle and a jar everywhere else.
    private static Stream<String> resourceNames() throws IOException {
        var module = ForeignSurface.class.getModule();
        if (module.isNamed() && module.getLayer() != null) {
            var reference = module.getLayer()
                    .configuration()
                    .findModule(module.getName())
                    .orElseThrow()
                    .reference();
            var reader = reference.open();
            return reader.list().onClose(() -> close(reader));
        }
        Path location;
        try {
            location = Path.of(ForeignSurface.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IOException("the code source of " + ForeignSurface.class.getName() + " is not a file", e);
        }
        if (Files.isDirectory(location)) {
            return relativeNames(location, location);
        }
        var jar = FileSystems.newFileSystem(location);
        var root = jar.getPath("/");
        return relativeNames(root, root).onClose(() -> close(jar));
    }

    private static Stream<String> relativeNames(Path root, Path start) throws IOException {
        return Files.walk(start)
                .filter(Files::isRegularFile)
                .map(file -> root.relativize(file).toString().replace(java.io.File.separatorChar, '/'));
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void initialise(String binaryName) {
        try {
            Class.forName(binaryName, true, ForeignSurface.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("listed by the module and not loadable: " + binaryName, e);
        }
    }
}
