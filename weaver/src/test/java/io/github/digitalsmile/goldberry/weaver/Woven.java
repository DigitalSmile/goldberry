package io.github.digitalsmile.goldberry.weaver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/// Weaves a class that is already on the test classpath, and loads the result.
///
/// `:weaver` deliberately does not apply `goldberry.weave` to itself, so the
/// models under `models/` are compiled exactly as an author wrote them. A test
/// reads those bytes back, weaves them, and defines the result in a loader of its
/// own — which is what lets one test hold both the raw class and the woven one
/// and compare them.
///
/// The loader delegates everything except the model itself to the test's own
/// loader, so `BoundModel`, `BindingRegistry` and `ActionRegistry` are the same types on both
/// sides. A loader that reloaded those too would produce a `BoundModel` that is
/// not the `BoundModel` the assertions import, and the failure would read as a
/// cast error with the same name twice.
final class Woven {

    private static final Map<String, Class<?>> CACHE = new HashMap<>();

    private Woven() {
    }

    /// The woven form of `type`, loaded fresh.
    static Class<?> of(Class<?> type) {
        return CACHE.computeIfAbsent(type.getName(), name -> define(type, weave(type)));
    }

    /// A new instance of the woven form of `type`.
    static Object instance(Class<?> type) {
        try {
            return of(type).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not instantiate woven " + type.getName(), e);
        }
    }

    /// The woven bytes, or null when the weaver left the class alone.
    static byte[] weave(Class<?> type) {
        return ModelWeaver.weave(bytesOf(type));
    }

    /// The class file as javac produced it.
    static byte[] bytesOf(Class<?> type) {
        var resource = type.getName().replace('.', '/') + ".class";
        try (var in = type.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no class file for " + type.getName());
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Class<?> define(Class<?> type, byte[] woven) {
        if (woven == null) {
            throw new IllegalStateException(type.getName() + " was not woven");
        }
        // Parent-last for this one name, and parent-first for everything else.
        // The ordinary delegation would find the *raw* class in the parent --
        // it is on the test classpath, which is where these bytes came from --
        // and quietly hand back the thing this test exists to replace.
        var loader = new ClassLoader(type.getClassLoader()) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(type.getName())) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    var found = findLoadedClass(name);
                    if (found == null) {
                        found = defineClass(name, woven, 0, woven.length);
                    }
                    if (resolve) {
                        resolveClass(found);
                    }
                    return found;
                }
            }
        };
        try {
            return Class.forName(type.getName(), true, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("woven " + type.getName() + " did not load", e);
        }
    }
}
