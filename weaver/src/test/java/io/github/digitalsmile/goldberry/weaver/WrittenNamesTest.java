package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.constant.ClassDesc;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Every class name this weaver *writes* still names a class.
///
/// The weaver does not import the toolkit types it weaves against — it emits
/// their descriptors into somebody else's class file, so they are `ClassDesc`
/// constants built from strings. `weaver/build.gradle` says the dependency on
/// `:core` is there because "the weaver names them; a string would be a name
/// nothing checks", and that was true of the *annotations* and false of these:
/// nothing checked them at all.
///
/// ADR-0172
/// moved `BoundModel`, `FieldListeners`, `BindingRegistry` and `ActionRegistry`
/// into packages of their own, and every one of those names is written here as
/// text. The build stayed green through the move; a woven native image would
/// have failed at class-load time, months later, with a `NoClassDefFoundError`
/// naming a package that no longer exists.
///
/// So: reflect over the weaver's own constants and resolve each one. Nothing is
/// listed by hand, which is the point — a constant added tomorrow is checked
/// tomorrow.
@DisplayName("the names the weaver writes")
class WrittenNamesTest {

    /// The `ClassDesc` constants a weaver holds, as their binary names.
    private static List<String> writtenNames(Class<?> weaver) {
        var names = new ArrayList<String>();
        for (var field : weaver.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != ClassDesc.class) {
                continue;
            }
            field.setAccessible(true);
            try {
                var desc = (ClassDesc) field.get(null);
                names.add(desc.packageName().isEmpty()
                        ? desc.displayName()
                        : desc.packageName() + "." + desc.displayName());
            } catch (IllegalAccessException e) {
                throw new AssertionError("cannot read " + weaver.getName() + "." + field.getName(), e);
            }
        }
        assertTrue(names.size() > 5,
                "found only " + names + " -- this test reflects over the weaver's ClassDesc "
                        + "constants, and finding almost none means it is checking nothing");
        return names;
    }

    /// A name the weaver writes but whose class is deliberately absent here.
    ///
    /// `Inflatable$Catalog` and everything else in `:widgets` cannot be on this
    /// module's test path: `:widgets` is *woven by* this module, so depending on
    /// it would be a build cycle. `WidgetCatalogTest` in `:widgets` covers that
    /// half end to end — it fails if the service the weaver declared cannot be
    /// loaded, which is the same name arriving from the other direction.
    private static boolean isWidgets(String name) {
        return name.startsWith("io.github.digitalsmile.goldberry.widgets.");
    }

    private void assertAllResolve(Class<?> weaver) {
        var missing = new ArrayList<String>();
        for (var name : writtenNames(weaver)) {
            if (isWidgets(name)) {
                continue;
            }
            try {
                Class.forName(name, false, WrittenNamesTest.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(),
                weaver.getSimpleName() + " writes these names into woven bytecode and no class "
                        + "answers to them, so the weave compiles and the woven image does not "
                        + "start: " + missing);
    }

    @Test
    @DisplayName("resolve, for the model weaver")
    void modelWeaverNamesResolve() {
        assertAllResolve(ModelWeaver.class);
    }

    @Test
    @DisplayName("resolve, for the catalog weaver")
    void catalogWeaverNamesResolve() {
        assertAllResolve(CatalogWeaver.class);
    }
}
