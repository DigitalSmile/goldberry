package io.github.digitalsmile.goldberry.weaver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /// Weaves a set of classes **together**, and loads all of them into one
    /// loader.
    ///
    /// What [WeaverMain] does to a directory, in memory: pass one collects which
    /// classes are models and which of them are written to from outside their own
    /// nest, pass two rewrites everything against that. It takes a group rather
    /// than one class because the interesting rule needs more than one — a write
    /// to a `@Bind` field from an actions class beside it is a `putfield` in a
    /// *different* class file, and nothing in either alone says it should be
    /// rewritten (ADR-0134).
    ///
    /// One loader for the group, so the woven `Actions` calls the woven `Values`
    /// rather than the raw one still sitting on the test classpath.
    ///
    /// @return each input class mapped to its woven form, by name
    static Map<String, Class<?>> group(Class<?>... types) {
        var raw = new LinkedHashMap<String, byte[]>();
        var internal = new LinkedHashMap<String, String>();
        for (var type : types) {
            raw.put(type.getName(), bytesOf(type));
            internal.put(type.getName().replace('.', '/'), type.getName());
        }
        var models = new LinkedHashMap<String, ModelWeaver.Rewired>();
        for (var bytes : raw.values()) {
            var rewired = ModelWeaver.rewired(bytes);
            if (rewired != null) {
                var descriptor = rewired.owner().descriptorString();
                models.put(descriptor.substring(1, descriptor.length() - 1), rewired);
            }
        }
        // Which models are reached from outside their own nest, and so need a
        // setter the package can call rather than a private one.
        var open = new HashSet<String>();
        for (var bytes : raw.values()) {
            var host = ModelWeaver.nestHost(bytes);
            for (var written : ModelWeaver.modelsWrittenBy(bytes, models)) {
                var owner = raw.get(internal.get(written));
                if (owner != null && !ModelWeaver.nestHost(owner).equals(host)) {
                    open.add(written);
                }
            }
        }
        var woven = new LinkedHashMap<String, byte[]>();
        raw.forEach((name, bytes) -> {
            var result = ModelWeaver.weave(bytes, models, open);
            woven.put(name, result == null ? bytes : result);
        });
        return define(woven, types[0].getClassLoader());
    }

    /// Defines a whole group parent-last, so each of them sees the others.
    private static Map<String, Class<?>> define(Map<String, byte[]> woven, ClassLoader parent) {
        var loader = new ClassLoader(parent) {

            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!woven.containsKey(name)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    var found = findLoadedClass(name);
                    if (found == null) {
                        var bytes = woven.get(name);
                        found = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(found);
                    }
                    return found;
                }
            }
        };
        var loaded = new LinkedHashMap<String, Class<?>>();
        for (var name : woven.keySet()) {
            try {
                loaded.put(name, Class.forName(name, true, loader));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("woven " + name + " did not load", e);
            }
        }
        return loaded;
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
