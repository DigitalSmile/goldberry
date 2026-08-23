package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.widget.Widget;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `RecordWitherTest`'s check, applied to **every widget in the catalog** rather
/// than to `Box` and `ComputedStyle`.
///
/// The argument moved. [ADR-0181](../../../../../../book/src/adr/0181-a-box-may-say-how-small-and-how-large.md)
/// made it about the two widest records in `:core`; since then `Select` has grown
/// to twelve components across four sessions of adding options to it — `multiple`,
/// `autocomplete`, `free`, `onQuery`, `tree` — and every one of those churned
/// every hand-written positional copy in the file. That is where an argument
/// lands in the wrong slot, and it compiles whenever the two components share a
/// type ([ADR-0185]).
///
/// ## The check needs no value factory
///
/// Every wither is asked to set its component to the value it **already has**,
/// and the result must equal the original. A wither that writes its argument into
/// the wrong slot, reads the wrong component into a slot, or passes one component
/// twice all fail it — provided no two components of one type are equal, which is
/// what [#distinctFor] arranges by building the fixture through the canonical
/// constructor with a different value per component.
///
/// It is therefore **automatic**: a widget added tomorrow is covered the moment
/// it appears in the catalog, and so is a component added to one that is here.
class WidgetWitherTest {

    /// Every widget record in the module, found by **walking the compiled
    /// classes** — `HolderShapeTest`'s approach, and for its reason: a list
    /// somebody has to remember to extend is a list that stops being true.
    private static List<Class<?>> widgets() throws Exception {
        var root = java.nio.file.Path.of(Widget.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        // The widgets module's own output, beside `:core`'s.
        var classes = java.nio.file.Path.of(
                Widgets.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var out = new ArrayList<Class<?>>();
        try (var found = java.nio.file.Files.walk(classes)) {
            for (var file : found.filter(f -> f.toString().endsWith(".class")).toList()) {
                var name = classes.relativize(file).toString()
                        .replace(java.io.File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                if (name.equals("module-info")) {
                    continue;
                }
                Class<?> type;
                try {
                    type = Class.forName(name, false, WidgetWitherTest.class.getClassLoader());
                } catch (Throwable e) {
                    continue;
                }
                if (type.isRecord() && Widget.class.isAssignableFrom(type)) {
                    out.add(type);
                }
            }
        }
        if (out.isEmpty()) {
            throw new AssertionError("no widget records were found under " + classes
                    + " (core is at " + root + "), so this test covers nothing");
        }
        return out;
    }

    @Test
    @DisplayName("every wither on every widget changes its own component and nothing else")
    void everyWidget() throws Exception {
        var checked = 0;
        var covered = new ArrayList<String>();
        var failures = new ArrayList<String>();

        for (var type : widgets()) {
            Object fixture;
            try {
                fixture = populate(type);
            } catch (ReflectiveOperationException | RuntimeException | AssertionError e) {
                // A widget whose canonical constructor refuses the values this
                // can invent -- a validated range, a required relationship
                // between two components. Recorded rather than skipped silently,
                // so the count below cannot quietly go to zero.
                failures.add(type.getSimpleName() + " (could not be built: " + e + ")");
                continue;
            }
            var found = check(type, fixture);
            if (found > 0) {
                covered.add(type.getSimpleName());
                checked += found;
            }
        }

        // A floor rather than an exact count: widgets are added, and a test that
        // had to be edited for each one is the list this walks the classes to
        // avoid. What it guards is the check silently covering nothing.
        assertTrue(checked >= 20 && covered.size() >= 10,
                "only " + checked + " withers were checked across " + covered.size()
                        + " widgets, which is fewer than the catalog has —"
                        + " the check is looking for the wrong shape."
                        + " Could not be built: " + failures);
    }

    /// Asks every wither to set its component to what it already holds.
    private static int check(Class<?> type, Object original) throws Exception {
        var components = new LinkedHashMap<String, RecordComponent>();
        for (var component : type.getRecordComponents()) {
            components.put(component.getName(), component);
        }
        var distinct = distinctFor(type, original);

        var checked = 0;
        for (var method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != type || method.getParameterCount() != 1) {
                continue;
            }
            var component = components.get(method.getName());
            if (component == null
                    || !method.getParameterTypes()[0].equals(component.getType())
                    || !distinct.contains(component.getName())) {
                continue;
            }
            method.setAccessible(true);
            var current = raw(type, component.getName(), original);
            var result = method.invoke(original, current);

            assertEquals(original, result,
                    type.getSimpleName() + "." + method.getName()
                            + "(its own value) did not give back an equal record, so one of its"
                            + " arguments is in the wrong slot");
            checked++;
        }
        return checked;
    }

    /// The components whose value is unique among components of their type.
    ///
    /// A swap between two equal components is invisible, so those are not
    /// checked rather than being checked vacuously — which for a widget built
    /// from defaults is most of them, and is why the fixture below tries to give
    /// each one something of its own.
    private static java.util.Set<String> distinctFor(Class<?> type, Object fixture)
            throws Exception {
        var byType = new LinkedHashMap<Class<?>, List<String>>();
        var values = new LinkedHashMap<String, Object>();
        for (var component : type.getRecordComponents()) {
            values.put(component.getName(), raw(type, component.getName(), fixture));
            byType.computeIfAbsent(component.getType(), k -> new ArrayList<>())
                    .add(component.getName());
        }
        var distinct = new java.util.LinkedHashSet<String>();
        for (var names : byType.values()) {
            for (var name : names) {
                var mine = values.get(name);
                var unique = names.stream()
                        .filter(other -> !other.equals(name))
                        .noneMatch(other -> java.util.Objects.equals(values.get(other), mine));
                if (unique) {
                    distinct.add(name);
                }
            }
        }
        return distinct;
    }

    /// A component's value read off the **field** rather than through its
    /// accessor.
    ///
    /// A record accessor can be overridden, and in this catalog several are:
    /// `SplitPaneView.children()` computes a divider between two panes rather
    /// than handing back the list it was built with, and calling it on a fixture
    /// with no panes throws. The field is the component; the accessor is a method
    /// that happens to share its name.
    private static Object raw(Class<?> type, String name, Object instance) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    /// Builds a widget through its canonical constructor with a different value
    /// per component, so that no two of one type are equal.
    private static Object populate(Class<?> type) throws ReflectiveOperationException {
        var components = type.getRecordComponents();
        var types = new Class<?>[components.length];
        var args = new Object[components.length];
        for (var i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            args[i] = value(components[i].getType(), i);
        }
        var constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    /// A value of `type` that varies with `index`, or null for anything this does
    /// not know how to make — a null component is simply not distinct, so it is
    /// skipped rather than guessed at.
    private static Object value(Class<?> type, int index) {
        if (type == String.class) {
            return "v" + index;
        }
        if (type == int.class) {
            return index + 1;
        }
        if (type == long.class) {
            return (long) index + 1;
        }
        if (type == double.class) {
            return index + 1.0;
        }
        if (type == float.class) {
            return index + 1.0f;
        }
        if (type == boolean.class) {
            // Both booleans on a widget would be equal half the time, so they are
            // simply never distinct and never checked. False, because a widget's
            // compact constructor is likelier to accept it.
            return false;
        }
        if (type == List.class) {
            return List.of();
        }
        if (type == java.util.Set.class) {
            return java.util.Set.of();
        }
        if (type == io.github.digitalsmile.goldberry.widget.attr.Attributes.class) {
            return new io.github.digitalsmile.goldberry.widget.attr.Attributes(
                    "id" + index, java.util.Set.of("c" + index), "k" + index);
        }
        return null;
    }
}
