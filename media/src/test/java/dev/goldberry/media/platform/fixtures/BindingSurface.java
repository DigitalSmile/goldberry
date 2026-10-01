package dev.goldberry.media.platform.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// The foreign-call surface a system's bindings package declares, read by
/// reflection: what each `…Bindings` class's lists are checked against, so a
/// binding added without being listed fails a test rather than a native image.
public final class BindingSurface {

    private BindingSurface() {}

    /// Every class in `anchor`'s package, from its main output, that holds a
    /// binding's unbound handle: a `static` `MethodHandle` named `FD_…`.
    public static List<Class<?>> linking(Class<?> anchor) {
        return classes(anchor).stream()
                .filter(type -> Arrays.stream(type.getDeclaredFields()).anyMatch(BindingSurface::isDowncallConstant))
                .toList();
    }

    /// Every `static` `FunctionDescriptor` field in `anchor`'s package: the
    /// callback shapes the bindings declare.
    public static List<FunctionDescriptor> callbacks(Class<?> anchor) {
        return classes(anchor).stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == FunctionDescriptor.class)
                .map(BindingSurface::read)
                .toList();
    }

    /// Whether `field` is a binding's unbound handle, `static … FD_…`.
    public static boolean isDowncallConstant(Field field) {
        return Modifier.isStatic(field.getModifiers())
                && field.getType() == MethodHandle.class
                && field.getName().startsWith("FD_");
    }

    /// Every top-level and nested class compiled into `anchor`'s package's main
    /// output, loaded but not initialised. The tests run from the class
    /// directories, not a jar, and the main one is found through `anchor`'s own
    /// code source, so the test classes of the same package are not in it.
    public static List<Class<?>> classes(Class<?> anchor) {
        var packageName = anchor.getPackageName();
        try {
            var root = Path.of(
                    anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
            try (var files = Files.list(root.resolve(packageName.replace('.', '/')))) {
                return files.map(file -> file.getFileName().toString())
                        .filter(name -> name.endsWith(".class"))
                        .map(name -> packageName + "." + name.substring(0, name.length() - ".class".length()))
                        .<Class<?>>map(name -> load(name, anchor))
                        .toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Class<?> load(String name, Class<?> anchor) {
        try {
            return Class.forName(name, false, anchor.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static FunctionDescriptor read(Field field) {
        try {
            field.setAccessible(true);
            return (FunctionDescriptor) Objects.requireNonNull(field.get(null));
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
