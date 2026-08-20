package io.github.digitalsmile.goldberry.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// What a model does in a jar the weaver never ran over (ADR-0155).
///
/// `:core` deliberately does not apply `goldberry.weave`, so every model below is
/// compiled exactly as it is written and every assertion here is about the
/// reflective path. The woven path's equivalent lives in `:weaver`, and
/// `RuntimeAgreesWithWovenTest` is what holds the two to the same answers.
@DisplayName("a model bound at run time")
class RuntimeBindingTest {

    /// A model of the usual shape: plain fields, plain methods, nothing about
    /// binding in the source at all.
    @Model
    static final class Settings {

        @Bind("app.gain") private int gain = 40;
        @Bind(value = "app.theme", restyle = true) private String theme = "dark";
        @Bind(value = "job.bytes", repaint = false) private long bytes;

        @Action("app.louder")
        private void louder() {
            gain++;
        }

        @Action("app.set-gain")
        private void setGain(int value) {
            gain = value;
        }

        @Action("app.pick")
        void pick(String name) {
            theme = name;
        }

        @Action("app.read")
        void read(double megabytes) {
            bytes = (long) (megabytes * 1_000_000);
        }

        @Action("app.everything")
        void everything() {
            gain = 1;
            theme = "light";
            bytes = 2;
        }
    }

    /// Every primitive shape, so the boxing the read does is exercised for each.
    @Model
    static final class EveryType {

        @Bind("t.z") private boolean z;
        @Bind("t.b") private byte b;
        @Bind("t.c") private char c;
        @Bind("t.s") private short s;
        @Bind("t.i") private int i;
        @Bind("t.j") private long j;
        @Bind("t.f") private float f;
        @Bind("t.d") private double d;
        @Bind("t.o") private String o;

        @Action("t.set")
        private void set() {
            z = true;
            b = 1;
            c = 'x';
            s = 2;
            i = 3;
            j = 4L;
            f = 5.5f;
            d = 6.5;
            o = "seven";
        }

        @Action("t.nan")
        private void nan() {
            d = Double.NaN;
        }

        @Action("t.negativeZero")
        private void negativeZero() {
            d = -0.0;
        }

        @Action("t.flag")
        void flag(boolean value) {
            z = value;
        }
    }

    /// A model holding a value somebody else owns. Already observable, so nothing
    /// about it is swept.
    @Model
    static final class Holder {

        @Bind("shared.tab") private final Property<String> tab = Property.of("one");
        @Bind("own.count") private int count;

        @Action("own.bump")
        void bump() {
            count++;
        }
    }

    /// The window's actions: no values at all, and a record, which is the shape
    /// ADR-0138 asks for.
    @Actions
    record WindowActions(List<String> log) {

        @Action("win.open")
        void open() {
            log.add("open");
        }
    }

    private static List<Object> watch(Observable<?> value) {
        var seen = new ArrayList<>();
        value.subscribe(seen::add);
        return seen;
    }

    @Nested
    @DisplayName("reads the fields it was pointed at")
    class Reading {

        @Test
        @DisplayName("every declared path resolves, and to the field's current value")
        void resolves() {
            var model = new Settings();

            assertEquals(40, Models.observable(model, "app.gain").get());
            assertEquals("dark", Models.observable(model, "app.theme").get());
            assertEquals(0L, Models.observable(model, "job.bytes").get());
        }

        @Test
        @DisplayName("a read is live: the field is the cell, not a copy of it")
        void live() {
            var model = new Settings();
            var gain = Models.<Integer>observable(model, "app.gain");

            model.gain = 7;

            // No sweep, no notification -- and still the current value, because
            // reading goes through a VarHandle onto the field itself.
            assertEquals(7, gain.get());
        }

        @Test
        @DisplayName("each primitive arrives boxed as its own type")
        void everyType() {
            var model = new EveryType();
            Models.actions(model).resolve("t.set").run();

            var bindings = Models.bindings(model);
            assertEquals(List.of(true, (byte) 1, 'x', (short) 2, 3, 4L, 5.5f, 6.5, "seven"),
                    List.of("t.z", "t.b", "t.c", "t.s", "t.i", "t.j", "t.f", "t.d", "t.o").stream()
                            .map(path -> bindings.resolve(path).get()).toList());
        }

        @Test
        @DisplayName("an unknown path is refused, because the registry is strict")
        void strict() {
            var model = new Settings();

            assertThrows(IllegalArgumentException.class,
                    () -> Models.observable(model, "app.gian"));
        }

        @Test
        @DisplayName("the registry is built once and kept, so a reload keeps its values")
        void registryKept() {
            var model = new Settings();

            assertSame(Models.bindings(model), Models.bindings(model));
            assertSame(Models.observable(model, "app.gain"), Models.observable(model, "app.gain"));
        }
    }

    @Nested
    @DisplayName("notices a change when it is swept")
    class Sweeping {

        @Test
        @DisplayName("a write with no sweep notifies nobody; the sweep is what does")
        void sweptNotWritten() {
            var model = new Settings();
            var seen = watch(Models.observable(model, "app.gain"));

            model.gain = 41;
            assertEquals(List.of(), seen);

            assertTrue(Models.refresh(model));
            assertEquals(List.of(41), seen);
        }

        @Test
        @DisplayName("a sweep over an unchanged model notifies nobody and says so")
        void quiet() {
            var model = new Settings();
            var seen = watch(Models.observable(model, "app.gain"));

            assertFalse(Models.refresh(model));
            assertEquals(List.of(), seen);
        }

        @Test
        @DisplayName("a write of the value already there is not a change")
        void sameValue() {
            var model = new Settings();
            var seen = watch(Models.observable(model, "app.gain"));

            model.gain = 40;

            assertFalse(Models.refresh(model));
            assertEquals(List.of(), seen);
        }

        @Test
        @DisplayName("two writes between sweeps are one notification, of the last value")
        void coalesced() {
            var model = new Settings();
            var seen = watch(Models.observable(model, "app.gain"));

            model.gain = 41;
            model.gain = 42;
            Models.refresh(model);

            assertEquals(List.of(42), seen);
        }

        @Test
        @DisplayName("NaN counts as a change from NaN once, the way a boxed comparison does")
        void nan() {
            var model = new EveryType();
            var seen = watch(Models.observable(model, "t.d"));

            Models.actions(model).resolve("t.nan").run();
            Models.actions(model).resolve("t.nan").run();

            assertEquals(1, seen.size());
            assertTrue(Double.isNaN((Double) seen.getFirst()));
        }

        @Test
        @DisplayName("-0.0 is a change from 0.0, the way a boxed comparison says")
        void negativeZero() {
            var model = new EveryType();
            var seen = watch(Models.observable(model, "t.d"));

            Models.actions(model).resolve("t.negativeZero").run();

            assertEquals(List.of(-0.0), seen);
        }

        @Test
        @DisplayName("a closed subscription is not notified, and the count says so")
        void unsubscribed() {
            var model = new Settings();
            var gain = Models.<Integer>observable(model, "app.gain");
            var seen = new ArrayList<Integer>();
            var subscription = gain.subscribe(seen::add);

            assertEquals(1, ((BoundField<?>) gain).listenerCount());
            subscription.close();
            assertEquals(0, ((BoundField<?>) gain).listenerCount());

            model.gain = 41;
            Models.refresh(model);

            assertEquals(List.of(), seen);
        }

        @Test
        @DisplayName("a listener that writes back does not re-enter the sweep")
        void reentrant() {
            var model = new Settings();
            var seen = new ArrayList<Integer>();
            Models.<Integer>observable(model, "app.gain").subscribe(value -> {
                seen.add(value);
                if (value < 45) {
                    model.gain = value + 1;
                    // Re-entrant, and deliberately a no-op: the write is picked up
                    // by the next sweep rather than by this one.
                    assertFalse(Models.refresh(model));
                }
            });

            model.gain = 41;
            Models.refresh(model);
            assertEquals(List.of(41), seen);

            Models.refresh(model);
            assertEquals(List.of(41, 42), seen);
        }
    }

    @Nested
    @DisplayName("asks for the frame the change needs")
    class Frames {

        @Test
        @DisplayName("a changed field asks for a frame; one declared repaint = false does not")
        void repaint() {
            var model = new Settings();
            var frames = new ArrayList<String>();
            Models.onRepaint(model, () -> frames.add("frame"));

            model.bytes = 99;
            Models.refresh(model);
            assertEquals(List.of(), frames);

            model.gain = 41;
            Models.refresh(model);
            assertEquals(List.of("frame"), frames);
        }

        @Test
        @DisplayName("a restyle field restyles first, then notifies, then asks for a frame")
        void restyleOrder() {
            var model = new Settings();
            var order = new ArrayList<String>();
            Models.onRestyle(model, () -> order.add("restyle"));
            Models.observable(model, "app.theme").subscribe(value -> order.add("listener"));
            Models.onRepaint(model, () -> order.add("frame"));

            model.theme = "light";
            Models.refresh(model);

            assertEquals(List.of("restyle", "listener", "frame"), order);
        }

        @Test
        @DisplayName("a field that is not a restyle one does not restyle")
        void noRestyle() {
            var model = new Settings();
            var restyles = new ArrayList<String>();
            Models.onRestyle(model, () -> restyles.add("restyle"));

            model.gain = 41;
            Models.refresh(model);

            assertEquals(List.of(), restyles);
        }

        @Test
        @DisplayName("three fields moved by one action ask for three frames, as woven does")
        void threeFrames() {
            var model = new Settings();
            var frames = new ArrayList<String>();
            Models.onRepaint(model, () -> frames.add("frame"));

            Models.actions(model).resolve("app.everything").run();

            // `job.bytes` is repaint = false, so two of the three ask.
            assertEquals(List.of("frame", "frame"), frames);
        }
    }

    @Nested
    @DisplayName("dispatches the actions it was pointed at")
    class Actions0 {

        @Test
        @DisplayName("a private no-argument method is reached")
        void privateAction() {
            var model = new Settings();

            Models.actions(model).resolve("app.louder").run();

            assertEquals(41, model.gain);
        }

        @Test
        @DisplayName("an action sweeps after itself, so a document needs no refresh")
        void sweepsAfterItself() {
            var model = new Settings();
            var seen = watch(Models.observable(model, "app.gain"));

            Models.actions(model).resolve("app.louder").run();

            assertEquals(List.of(41), seen);
        }

        @Test
        @DisplayName("a valued action is handed the string parsed to its parameter")
        void valued() {
            var model = new Settings();
            var actions = Models.actions(model);

            actions.resolveValued("app.set-gain").accept("77");
            actions.resolveValued("app.pick").accept("light");
            actions.resolveValued("app.read").accept("1.5");

            assertEquals(77, model.gain);
            assertEquals("light", model.theme);
            assertEquals(1_500_000L, model.bytes);
        }

        @Test
        @DisplayName("a boolean parameter is parsed too")
        void valuedBoolean() {
            var model = new EveryType();

            Models.actions(model).resolveValued("t.flag").accept("true");

            assertTrue(model.z);
        }

        @Test
        @DisplayName("a valued action does not adapt down to one that takes nothing")
        void notAdapted() {
            var actions = Models.actions(new Settings());

            assertThrows(IllegalArgumentException.class, () -> actions.resolve("app.pick"));
        }

        @Test
        @DisplayName("the registry is fresh each call, so an application may add to it")
        void freshRegistry() {
            var model = new Settings();

            assertNotSame(Models.actions(model), Models.actions(model));
            Models.actions(model).bind("win.close", () -> { });
        }

        @Test
        @DisplayName("an @Actions record publishes its methods and no bindings")
        void actionsOnly() {
            var log = new ArrayList<String>();
            var actions = new WindowActions(log);

            Models.actions(actions).resolve("win.open").run();

            assertEquals(List.of("open"), log);
            assertEquals(List.of(), List.copyOf(Models.bindings(actions).bound().keySet()));
        }

        @Test
        @DisplayName("two equal records are two models, because a binding is per instance")
        void byIdentity() {
            var log = new ArrayList<String>();
            var first = new WindowActions(log);
            var second = new WindowActions(log);

            assertEquals(first, second);
            assertNotSame(Models.actions(first), Models.actions(second));
        }
    }

    @Nested
    @DisplayName("leaves a Property alone")
    class Properties {

        @Test
        @DisplayName("a Property field is bound as itself, not wrapped")
        void boundDirectly() {
            var model = new Holder();

            assertSame(model.tab, Models.observable(model, "shared.tab"));
        }

        @Test
        @DisplayName("its own set notifies; the sweep has nothing to do with it")
        void notifiesItself() {
            var model = new Holder();
            var seen = watch(Models.observable(model, "shared.tab"));

            model.tab.set("two");

            assertEquals(List.of("two"), seen);
            assertFalse(Models.refresh(model));
        }

        @Test
        @DisplayName("it takes no slot, so the plain field beside it is slot zero")
        void takesNoSlot() {
            var model = new Holder();

            assertEquals(1, Models.bindings(model).bound().size() - 1);
            Models.actions(model).resolve("own.bump").run();
            assertEquals(1, Models.observable(model, "own.count").get());
        }
    }

    @Nested
    @DisplayName("says which form it is, and refuses what the weaver refuses")
    class Refusals {

        @Model
        static final class StaticBind {
            @Bind("bad.path") private static int shared;
        }

        @Model
        static final class FinalBind {
            @Bind("bad.path") private final int frozen = 1;
        }

        @Model
        static final class ArrayBind {
            @Bind("bad.path") private int[] values = new int[1];
        }

        @Model
        static final class TwicePathed {
            @Bind("bad.path") private int one;
            @Bind("bad.path") private int two;
        }

        @Model
        static final class RestylingProperty {
            @Bind(value = "bad.path", restyle = true) private final Property<String> p =
                    Property.of("x");
        }

        @Model
        static final class TwoArguments {
            @Action("bad.action") void act(String a, String b) {
                assertEquals(a, b);
            }
        }

        @Model
        static final class UnparseableArgument {
            @Action("bad.action") void act(List<String> values) {
                values.clear();
            }
        }

        @Model
        static final class StaticAction {
            @Action("bad.action") static void act() {
            }
        }

        @Model
        static class Parent {
            @Bind("parent.value") private int value;
        }

        @Model
        static final class Child extends Parent {
            @Bind("child.value") private int value;
        }

        @Model
        @Actions
        static final class Both {
        }

        static final class Unannotated {
        }

        @Test
        @DisplayName("a model bound at run time is not a woven one, and says so")
        void notWoven() {
            assertFalse(Models.isWoven(new Settings()));
        }

        @Test
        @DisplayName("a class annotated neither way is refused, with what to do about it")
        void unannotated() {
            var refusal = assertThrows(IllegalStateException.class,
                    () -> Models.bindings(new Unannotated()));

            assertTrue(refusal.getMessage().contains("annotated neither @Model nor @Actions"));
        }

        @Test
        @DisplayName("a static @Bind field")
        void staticBind() {
            assertRefused(new StaticBind(), "is static");
        }

        @Test
        @DisplayName("a final @Bind field that is not a Property")
        void finalBind() {
            assertRefused(new FinalBind(), "is final");
        }

        @Test
        @DisplayName("an array, because only the assignment is observed")
        void arrayBind() {
            assertRefused(new ArrayBind(), "is an array");
        }

        @Test
        @DisplayName("two fields claiming one path")
        void twicePathed() {
            assertRefused(new TwicePathed(), "claimed by both");
        }

        @Test
        @DisplayName("a Property asking for a restyle, which has nowhere to put the call")
        void restylingProperty() {
            assertRefused(new RestylingProperty(), "asks for a restyle");
        }

        @Test
        @DisplayName("an action taking two arguments")
        void twoArguments() {
            assertRefused(new TwoArguments(), "takes 2 arguments");
        }

        @Test
        @DisplayName("an action taking something no string parses to")
        void unparseable() {
            assertRefused(new UnparseableArgument(), "must be one of String, double, int, boolean");
        }

        @Test
        @DisplayName("a static action, which has no model to change")
        void staticAction() {
            assertRefused(new StaticAction(), "has no model to change");
        }

        @Test
        @DisplayName("a model extending a model, which would notify neither reliably")
        void inheritance() {
            assertRefused(new Child(), "which is also a model");
        }

        @Test
        @DisplayName("a class that is both @Model and @Actions")
        void both() {
            assertRefused(new Both(), "annotated both @Model and @Actions");
        }

        private static void assertRefused(Object model, String because) {
            var refusal = assertThrows(IllegalStateException.class,
                    () -> Models.bindings(model));

            assertTrue(refusal.getMessage().contains(because),
                    () -> "expected a refusal mentioning \"" + because + "\", got: "
                            + refusal.getMessage());
        }
    }
}
