package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;

/// The one failure mode a 24-component record with 25 hand-written copies has:
/// **an argument in the wrong slot**.
///
/// `Box` and `ComputedStyle` each carry every layout and paint property the
/// engine knows, and each rebuilds itself positionally in every one of its
/// withers. Adding a component — `Limits` was the last, and there will be more —
/// means editing 45 argument lists between them, any one of which can put
/// `height` where `width` goes and produce a record that compiles, runs, and is
/// subtly wrong in a way no golden would obviously show
/// (ADR-0181).
///
/// ## Why this is a test and not a refactor
///
/// The structural answer is to group the components into sub-records until no
/// argument list is long enough to get wrong — which is what `Insets` and
/// `Limits` already do for their four apiece. Doing that to the rest would touch
/// every accessor in the toolkit (`box.width()` becomes `box.layout().width()`)
/// for a benefit this catches completely and immediately.
///
/// ## The trick, which is that no value factory is needed
///
/// Every wither is asked to set the component **to the value it already has**,
/// and the result must equal the original. That is a complete check: a wither
/// that writes its argument into the wrong slot, or reads the wrong component
/// into a slot, or passes one component twice, all produce a record that differs
/// — provided no two components of the same type hold equal values, which
/// [#componentsAreDistinct] is here to guarantee.
///
/// It also needs nothing per type and nothing per component, so a component
/// added tomorrow is covered by this the moment its wither exists.
class RecordWitherTest {

    /// A box with every component set, and no two of the same type equal.
    ///
    /// Written through the canonical constructor rather than by chaining the
    /// withers, which would be circular: a broken wither would build the fixture
    /// its own test then checked it against.
    private static Box box() {
        return new Box(
                0xFF102030,
                io.github.digitalsmile.goldberry.css.Decoration.NONE,
                0.5,
                io.github.digitalsmile.goldberry.css.value.Transform.of(
                        new io.github.digitalsmile.goldberry.css.value.Transform.Function.Translate(
                                io.github.digitalsmile.goldberry.css.value.Transform.Length.px(3),
                                io.github.digitalsmile.goldberry.css.value.Transform.Length.px(4))),
                io.github.digitalsmile.goldberry.render.Cursor.POINTER,
                io.github.digitalsmile.goldberry.layout.FlexDirection.COLUMN,
                io.github.digitalsmile.goldberry.layout.Justify.CENTER,
                io.github.digitalsmile.goldberry.layout.Align.FLEX_END,
                // `alignSelf`, and deliberately not `FLEX_END`: two components of
                // one type holding equal values is exactly what
                // `componentsAreDistinct` refuses, because a swap between them
                // would be invisible to the check below.
                io.github.digitalsmile.goldberry.layout.Align.CENTER,
                io.github.digitalsmile.goldberry.layout.Wrap.WRAP_REVERSE,
                length(11),
                length(22),
                new io.github.digitalsmile.goldberry.layout.Limits(length(31), length(32), length(33), length(34)),
                new io.github.digitalsmile.goldberry.layout.Insets(length(41), length(42), length(43), length(44)),
                length(55),
                6,
                7,
                io.github.digitalsmile.goldberry.layout.Position.ABSOLUTE,
                new io.github.digitalsmile.goldberry.layout.Insets(length(61), length(62), length(63), length(64)),
                true,
                io.github.digitalsmile.goldberry.layout.Overflow.HIDDEN,
                null,
                null,
                new Box.Mark(Box.Mark.Kind.CHECK, 0xFF445566, 2),
                // A painter that draws nothing: the wither check needs a value
                // no other component equals, and a lambda's identity is that by
                // construction.
                (frame, size) -> {},
                List.of(Box.of()),
                "owner");
    }

    /// The same for a style. Its components overlap `Box`'s and its withers are
    /// the same shape, so the same check applies unchanged.
    private static ComputedStyle style() {
        return ComputedStyle.INITIAL
                .direction(io.github.digitalsmile.goldberry.layout.FlexDirection.COLUMN)
                .justifyContent(io.github.digitalsmile.goldberry.layout.Justify.CENTER)
                .alignItems(io.github.digitalsmile.goldberry.layout.Align.FLEX_END)
                .alignSelf(io.github.digitalsmile.goldberry.layout.Align.CENTER)
                .wrap(io.github.digitalsmile.goldberry.layout.Wrap.WRAP_REVERSE)
                .width(length(11))
                .height(length(22))
                .limits(new io.github.digitalsmile.goldberry.layout.Limits(
                        length(31), length(32), length(33), length(34)))
                .padding(new io.github.digitalsmile.goldberry.layout.Insets(
                        length(41), length(42), length(43), length(44)))
                .gap(length(55))
                .flexGrow(6)
                .flexShrink(7)
                .position(io.github.digitalsmile.goldberry.layout.Position.ABSOLUTE)
                .inset(new io.github.digitalsmile.goldberry.layout.Insets(
                        length(61), length(62), length(63), length(64)))
                .overflow(io.github.digitalsmile.goldberry.layout.Overflow.HIDDEN)
                .background(0xFF102030)
                .color(0xFF405060)
                .opacity(0.5)
                .cursor(io.github.digitalsmile.goldberry.render.Cursor.POINTER);
    }

    private static io.github.digitalsmile.goldberry.layout.Length length(float v) {
        return io.github.digitalsmile.goldberry.layout.Length.points(v);
    }

    /// Asks every wither to set its component to what it already holds, and
    /// requires the record back unchanged.
    ///
    /// @return how many withers were found, so a check that covers nothing
    ///         cannot pass quietly
    private static <T extends Record> int checkWithers(Class<T> type, T original) throws Exception {
        var components = new LinkedHashMap<String, RecordComponent>();
        for (var component : type.getRecordComponents()) {
            components.put(component.getName(), component);
        }

        var checked = 0;
        for (var method : type.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())
                    || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != type
                    || method.getParameterCount() != 1) {
                continue;
            }
            var component = components.get(method.getName());
            // A one-argument method named after a component, taking that
            // component's own type, is a wither. `Box.padding(Length)` is
            // not — it takes a length where the component is an `Insets`, and is
            // a convenience over the real one.
            if (component == null || !method.getParameterTypes()[0].equals(component.getType())) {
                continue;
            }
            var current = component.getAccessor().invoke(original);
            var result = method.invoke(original, current);

            assertEquals(
                    original,
                    result,
                    type.getSimpleName() + "." + method.getName()
                            + "(its own value) did not give back an equal record, so one of its"
                            + " arguments is in the wrong slot");
            checked++;
        }
        return checked;
    }

    /// The premise the check rests on: two components of one type holding equal
    /// values would make a swap between them invisible.
    private static <T extends Record> void componentsAreDistinct(Class<T> type, T fixture) throws Exception {
        var byType = new LinkedHashMap<Class<?>, List<String>>();
        var values = new LinkedHashMap<String, Object>();
        for (var component : type.getRecordComponents()) {
            var value = component.getAccessor().invoke(fixture);
            values.put(component.getName(), value);
            byType.computeIfAbsent(component.getType(), k -> new ArrayList<>()).add(component.getName());
        }
        for (var entry : byType.entrySet()) {
            var names = entry.getValue();
            for (var i = 0; i < names.size(); i++) {
                for (var j = i + 1; j < names.size(); j++) {
                    var a = values.get(names.get(i));
                    var b = values.get(names.get(j));
                    // Two nulls are indistinguishable and harmless: a swap
                    // between two null components produces the same record.
                    if (a == null && b == null) {
                        continue;
                    }
                    assertTrue(
                            a == null || !a.equals(b),
                            type.getSimpleName() + "'s " + names.get(i) + " and " + names.get(j)
                                    + " are both " + a + ", so this test could not tell them"
                                    + " apart if a wither swapped them");
                }
            }
        }
    }

    @Test
    @DisplayName("every Box wither changes its own component and nothing else")
    void boxWithers() throws Exception {
        var fixture = box();
        componentsAreDistinct(Box.class, fixture);

        var checked = checkWithers(Box.class, fixture);

        assertTrue(
                checked >= 15,
                "only " + checked + " withers were found on Box, which is fewer than it has —"
                        + " the check is looking for the wrong shape");
    }

    @Test
    @DisplayName("every ComputedStyle wither changes its own component and nothing else")
    void computedStyleWithers() throws Exception {
        var fixture = style();
        componentsAreDistinct(ComputedStyle.class, fixture);

        var checked = checkWithers(ComputedStyle.class, fixture);

        assertTrue(checked >= 15, "only " + checked + " withers were found on ComputedStyle");
    }
}
