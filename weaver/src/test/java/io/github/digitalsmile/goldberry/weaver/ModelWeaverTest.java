package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.bind.BindingRegistry;
import io.github.digitalsmile.goldberry.bind.BoundModel;
import io.github.digitalsmile.goldberry.bind.Models;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.weaver.models.Counter;
import io.github.digitalsmile.goldberry.weaver.models.EveryType;
import io.github.digitalsmile.goldberry.weaver.models.Refused;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// What the weaver does to a model, and what it refuses to do.
@DisplayName("the model weaver")
class ModelWeaverTest {

    /// The model under test, plus the two registries it now publishes.
    private record Model(Object instance, BindingRegistry bindings, ActionRegistry actions) {

        static Model of(Class<?> type) {
            var instance = Woven.instance(type);
            return new Model(instance, Models.bindings(instance), Models.actions(instance));
        }

        Observable<?> path(String path) {
            return bindings.resolve(path);
        }

        void press(String name) {
            actions.resolve(name).run();
        }

        void send(String name, String value) {
            actions.resolveValued(name).accept(value);
        }

        Object read(String method) {
            try {
                return instance.getClass().getMethod(method).invoke(instance);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /// Records everything one path reports, in order.
    private static List<Object> watch(Observable<?> observable) {
        var seen = new ArrayList<Object>();
        observable.subscribe(seen::add);
        return seen;
    }

    @Nested
    @DisplayName("rewiring a field")
    class Rewiring {

        @Test
        @DisplayName("a plain field write notifies")
        void writeNotifies() {
            var model = Model.of(Counter.class);
            var seen = watch(model.path("app.clicks"));

            model.press("app.reset");
            model.press("app.click");

            // reset() writes 0 over 0, which changes nothing and says nothing;
            // click() takes it to 1.
            assertEquals(List.of(1), seen);
        }

        @Test
        @DisplayName("the field is still the cell -- a read sees what the model wrote")
        void fieldIsTheCell() {
            var model = Model.of(Counter.class);

            model.press("app.click");
            model.press("app.click");

            assertEquals(2, model.read("rawClicks"));
            assertEquals(2, model.path("app.clicks").get());
        }

        @Test
        @DisplayName("a write that changes nothing notifies nobody")
        void unchangedIsSilent() {
            var model = Model.of(Counter.class);
            var seen = watch(model.path("app.label"));

            model.send("app.say", "hello");
            model.send("app.say", "hello");
            model.send("app.say", "hello");

            assertEquals(List.of("hello"), seen);
        }

        @Test
        @DisplayName("a write from inside a lambda is rewritten too")
        void lambdaWritesAreRewritten() {
            // click() assigns through a Runnable, which javac compiles into a
            // synthetic method of the same class. A weaver that only walked the
            // author's own methods would miss it, and the miss would look like a
            // button that works and a label that never moves.
            var model = Model.of(Counter.class);
            var seen = watch(model.path("app.clicks"));

            model.press("app.click");

            assertEquals(List.of(1), seen);
        }

        @Test
        @DisplayName("a subscription can be closed")
        void subscriptionCloses() {
            var model = Model.of(Counter.class);
            var seen = new ArrayList<Object>();
            var subscription = model.path("app.clicks").subscribe(seen::add);

            model.press("app.click");
            subscription.close();
            model.press("app.click");

            assertEquals(List.of(1), seen);
            assertEquals(2, model.read("rawClicks"));
        }

        @Test
        @DisplayName("closing twice is a no-op")
        void closingTwice() {
            var model = Model.of(Counter.class);
            var subscription = model.path("app.clicks").subscribe(v -> { });

            subscription.close();
            assertDoesNotThrow(subscription::close);
        }
    }

    @Nested
    @DisplayName("a change is its own frame request")
    class AnyChange {

        @Test
        @DisplayName("any bound field changing notifies once")
        void anyFieldNotifies() {
            var model = Model.of(Counter.class);
            var frames = new int[1];
            Models.onRepaint(model.instance(), () -> frames[0]++);

            model.press("app.click");
            model.send("app.say", "hello");

            assertEquals(2, frames[0]);
        }

        @Test
        @DisplayName("a write that changes nothing asks for no frame")
        void unchangedAsksForNothing() {
            // The improvement over the explicit `changed()` every action used to
            // end in: that one asked for a frame whether or not anything moved.
            var model = Model.of(Counter.class);
            var frames = new int[1];
            Models.onRepaint(model.instance(), () -> frames[0]++);

            model.press("app.reset");
            model.send("app.say", "idle");

            assertEquals(0, frames[0]);
        }

        @Test
        @DisplayName("one action moving two fields asks twice, and the scheduler coalesces")
        void oncePerChange() {
            var model = Model.of(Counter.class);
            model.press("app.click");
            model.send("app.say", "busy");

            var frames = new int[1];
            Models.onRepaint(model.instance(), () -> frames[0]++);

            // reset() writes both clicks and label, and both move.
            model.press("app.reset");

            assertEquals(2, frames[0]);
        }

        @Test
        @DisplayName("the per-field listeners run first, so a restyle precedes the frame")
        void orderIsFieldThenAny() {
            var model = Model.of(Counter.class);
            var order = new ArrayList<String>();
            model.path("app.clicks").subscribe(v -> order.add("field"));
            Models.onRepaint(model.instance(), () -> order.add("frame"));

            model.press("app.click");

            assertEquals(List.of("field", "frame"), order);
        }

        @Test
        @DisplayName("and it can be unsubscribed")
        void closes() {
            var model = Model.of(Counter.class);
            var frames = new int[1];
            var subscription = Models.onRepaint(model.instance(), () -> frames[0]++);

            model.press("app.click");
            subscription.close();
            model.press("app.click");

            assertEquals(1, frames[0]);
        }
    }

    @Nested
    @DisplayName("a restyle is declared, not subscribed to")
    class Restyle {

        @Test
        @DisplayName("a @Bind(restyle = true) field asks for one")
        void restyleFires() {
            var model = Model.of(Counter.class);
            var restyles = new int[1];
            Models.onRestyle(model.instance(), () -> restyles[0]++);

            model.send("app.pick-theme", "light");

            assertEquals(1, restyles[0]);
        }

        @Test
        @DisplayName("an ordinary field does not")
        void ordinaryFieldIsQuiet() {
            var model = Model.of(Counter.class);
            var restyles = new int[1];
            Models.onRestyle(model.instance(), () -> restyles[0]++);

            model.press("app.click");
            model.send("app.say", "hello");

            assertEquals(0, restyles[0]);
        }

        @Test
        @DisplayName("and neither does a write that changed nothing")
        void unchangedIsQuiet() {
            var model = Model.of(Counter.class);
            var restyles = new int[1];
            Models.onRestyle(model.instance(), () -> restyles[0]++);

            model.send("app.pick-theme", "dark");

            assertEquals(0, restyles[0]);
        }

        @Test
        @DisplayName("the restyle runs before the frame is asked for")
        void restylePrecedesTheFrame() {
            // A window that repainted before dropping its resolved styles would
            // paint one frame with the old theme.
            var model = Model.of(Counter.class);
            var order = new ArrayList<String>();
            Models.onRestyle(model.instance(), () -> order.add("restyle"));
            Models.onRepaint(model.instance(), () -> order.add("frame"));

            model.send("app.pick-theme", "light");

            assertEquals(List.of("restyle", "frame"), order);
        }

        @Test
        @DisplayName("a Property cannot ask for one")
        void propertyCannotRestyle() {
            var message = assertThrows(WeaveException.class,
                    () -> Woven.weave(Refused.RestylingProperty.class)).getMessage();

            assertTrue(message.contains("asks for a restyle"), message);
        }
    }

    @Nested
    @DisplayName("a frame is asked for by the value that moved")
    class PerFieldRepaint {

        @Test
        @DisplayName("a value declared repaint = false asks for none")
        void quietField() {
            var model = Model.of(Counter.class);
            var frames = new int[1];
            Models.onRepaint(model.instance(), () -> frames[0]++);

            model.press("app.tick");

            assertEquals(0, frames[0]);
        }

        @Test
        @DisplayName("but it still notifies whatever bound it")
        void quietFieldStillBinds() {
            // "Do not wake the window" is not "do not observe". Something else
            // may be watching a value nothing on screen shows.
            var model = Model.of(Counter.class);
            var seen = watch(model.path("app.ticks"));

            model.press("app.tick");

            assertEquals(List.of(1), seen);
        }

        @Test
        @DisplayName("and a value beside it in the same model still asks")
        void loudFieldStillAsks() {
            // The reason this is per value and not per model: one model holds
            // both, and a switch on the class would be wrong about one of them.
            var model = Model.of(Counter.class);
            var frames = new int[1];
            Models.onRepaint(model.instance(), () -> frames[0]++);

            model.press("app.click");

            assertEquals(1, frames[0]);
        }
    }

    @Nested
    @DisplayName("every field type")
    class Types {

        @Test
        @DisplayName("each shape notifies with its value, boxed")
        void allTypes() {
            var model = Model.of(EveryType.class);
            var seen = new ArrayList<Object>();
            for (var path : List.of("t.z", "t.b", "t.c", "t.s", "t.i", "t.j", "t.f", "t.d", "t.o")) {
                model.path(path).subscribe(seen::add);
            }

            model.press("t.set");

            assertEquals(List.of(true, (byte) 1, 'x', (short) 2, 3, 4L, 5.5f, 6.5, "seven"), seen);
        }

        @Test
        @DisplayName("NaN counts as a change from NaN, the way a boxed comparison does")
        void nan() {
            // Double.compare(NaN, NaN) == 0, so the second write is silent --
            // which is Objects.equals's answer, and the reason the setter uses
            // compare rather than ==. Under `==` every write of NaN would notify
            // forever.
            var model = Model.of(EveryType.class);
            var seen = watch(model.path("t.d"));

            model.press("t.nan");
            model.press("t.nan");

            assertEquals(1, seen.size());
            assertTrue(Double.isNaN((Double) seen.getFirst()));
        }

        @Test
        @DisplayName("-0.0 is a change from 0.0, the way a boxed comparison says")
        void negativeZero() {
            var model = Model.of(EveryType.class);
            var seen = watch(model.path("t.d"));

            model.press("t.negativeZero");

            assertEquals(List.of(-0.0), seen);
        }
    }

    @Nested
    @DisplayName("actions")
    class ActionBinding {

        @Test
        @DisplayName("a private method is bound, and reached")
        void privateAction() {
            var model = Model.of(Counter.class);

            model.press("app.click");

            assertEquals(1, model.read("rawClicks"));
        }

        @Test
        @DisplayName("a valued action parses what markup wrote down")
        void valuedActions() {
            var model = Model.of(Counter.class);

            model.send("app.set-gain", "2.5");
            model.send("app.set-clicks", "7");
            model.send("app.set-on", "true");
            model.send("app.say", "hello");

            assertEquals(2.5, model.path("app.gain").get());
            assertEquals(7, model.path("app.clicks").get());
            assertEquals(true, model.path("app.on").get());
            assertEquals("hello", model.path("app.label").get());
        }

        @Test
        @DisplayName("an action that returns something is called for its effect")
        void returningAction() {
            var model = Model.of(Counter.class);

            model.press("app.bump");

            assertEquals(1, model.read("rawClicks"));
        }

        @Test
        @DisplayName("the registry is strict, so a typo is refused")
        void strict() {
            var model = Model.of(Counter.class);

            assertThrows(IllegalArgumentException.class, () -> model.press("app.clcik"));
            assertThrows(IllegalArgumentException.class, () -> model.path("app.clcik"));
        }

        @Test
        @DisplayName("names are registered in declaration order")
        void order() {
            var model = Model.of(Counter.class);

            assertEquals(
                    List.of("app.click", "app.reset", "app.tick", "app.bump", "app.set-gain", "app.set-clicks",
                            "app.set-on", "app.say", "app.pick-theme"),
                    List.copyOf(model.actions().bound().keySet()));
        }
    }

    @Nested
    @DisplayName("a Property field")
    class Properties {

        @Test
        @DisplayName("is bound as itself, not copied")
        void boundDirectly() {
            var model = Model.of(Counter.class);
            var seen = watch(model.path("app.owned"));

            // The model's own Property, reached through the model's accessor --
            // so this is somebody outside writing to a value the model holds but
            // does not own. A weaver that copied it into a slot would leave this
            // binding reporting "outside" forever.
            @SuppressWarnings("unchecked")
            var owned = (io.github.digitalsmile.goldberry.bind.Property<String>) model.read("owned");
            assertEquals("outside", owned.get());

            owned.set("changed");

            assertEquals(List.of("changed"), seen);
            assertEquals("changed", model.path("app.owned").get());
        }

        @Test
        @DisplayName("a model with nothing but a Property still weaves")
        void propertyOnlyModel() {
            var model = Model.of(Refused.PropertyOnly.class);

            model.press("a.act");

            assertEquals("y", model.path("a.b").get());
        }
    }

    @Nested
    @DisplayName("the class the weaver produces")
    class Output {

        @Test
        @DisplayName("implements BoundModel")
        void implementsInterface() {
            assertTrue(BoundModel.class.isAssignableFrom(Woven.of(Counter.class)));
        }

        @Test
        @DisplayName("weaving twice changes nothing")
        void idempotent() {
            // The build task rewrites in place and is deliberately never up to
            // date, so it hands the weaver its own output on the next build. A
            // second pass has to be a no-op, or the class gains every synthesised
            // member twice and stops verifying.
            var once = Woven.weave(Counter.class);
            assertNotNull(once);
            assertNull(ModelWeaver.weave(once));
        }

        @Test
        @DisplayName("the registry itself is built once and kept")
        void bindingsAreCached() {
            // `Models.observable` is called while building a widget, which happens
            // every frame -- a fresh registry per lookup would be a cost nobody
            // goes looking for.
            var model = Model.of(Counter.class);

            assertSame(Models.bindings(model.instance()), Models.bindings(model.instance()));
        }

        @Test
        @DisplayName("but the action registry is fresh, because applications extend it")
        void actionsAreNotCached() {
            var model = Model.of(Counter.class);

            var first = Models.actions(model.instance());
            var second = Models.actions(model.instance());

            assertNotSame(first, second);
            // Which is the point: adding a window's own action to one must not
            // make the next caller fail with "already bound".
            first.bind("ui.close", () -> { });
            assertDoesNotThrow(() -> second.bind("ui.close", () -> { }));
        }

        @Test
        @DisplayName("resolving a path twice gives the same observable")
        void observablesAreStable() {
            // A reload rebuilds the registry, and the tree compares a widget's
            // binding against what the registry resolved. Two windows onto one
            // field that are not the same object would make that comparison
            // false for a value that has not moved.
            var model = Model.of(Counter.class);

            assertSame(model.path("app.clicks"), model.path("app.clicks"));
            assertSame(model.path("app.clicks"),
                    Models.bindings(model.instance()).resolve("app.clicks"));
        }

        @Test
        @DisplayName("a class that is not a @Model is left alone")
        void notAModel() {
            assertNull(Woven.weave(Refused.NotAModel.class));
            assertNull(ModelWeaver.weave(Woven.bytesOf(ModelWeaverTest.class)));
        }

        @Test
        @DisplayName("the author's own API is untouched")
        void authorsApiSurvives() {
            var woven = Woven.of(Counter.class);

            assertDoesNotThrow(() -> woven.getMethod("rawClicks"));
            assertDoesNotThrow(() -> woven.getDeclaredMethod("click"));
            // The field is still a field, and still private.
            assertDoesNotThrow(() -> woven.getDeclaredField("clicks"));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Refuses {

        private String refusal(Class<?> type) {
            return assertThrows(WeaveException.class, () -> Woven.weave(type)).getMessage();
        }

        @Test
        @DisplayName("a static @Bind field")
        void staticField() {
            assertTrue(refusal(Refused.StaticField.class).contains("is static"));
        }

        @Test
        @DisplayName("a final @Bind field that is not a Property")
        void finalField() {
            assertTrue(refusal(Refused.FinalField.class).contains("is final"));
        }

        @Test
        @DisplayName("an array, because only the assignment is observed")
        void arrayField() {
            assertTrue(refusal(Refused.ArrayField.class).contains("is an array"));
        }

        @Test
        @DisplayName("a path that is not a dotted path")
        void badPath() {
            assertTrue(refusal(Refused.BadPath.class).contains("not a dotted"));
        }

        @Test
        @DisplayName("two fields claiming one path")
        void duplicatePath() {
            assertTrue(refusal(Refused.DuplicatePath.class).contains("claimed by both"));
        }

        @Test
        @DisplayName("a field and an action claiming one name")
        void pathClash() {
            assertTrue(refusal(Refused.PathClashesWithAction.class).contains("claimed by both"));
        }

        @Test
        @DisplayName("an action taking two arguments")
        void twoArguments() {
            assertTrue(refusal(Refused.TwoArgumentAction.class).contains("takes 2 arguments"));
        }

        @Test
        @DisplayName("an action taking something a String cannot become")
        void unparseable() {
            assertTrue(refusal(Refused.UnparseableArgument.class).contains("crosses as a String"));
        }

        @Test
        @DisplayName("a static action")
        void staticAction() {
            assertTrue(refusal(Refused.StaticAction.class).contains("is static"));
        }

        @Test
        @DisplayName("a @Model with nothing in it")
        void empty() {
            assertTrue(refusal(Refused.Empty.class).contains("no @Bind or @Action"));
        }

        @Test
        @DisplayName("@Actions on a class that holds values")
        void actionsWithValues() {
            assertTrue(refusal(Refused.ActionsWithValues.class).contains("is a @Model"));
        }

        @Test
        @DisplayName("@Actions on a class with no actions")
        void actionsWithNoActions() {
            assertTrue(refusal(Refused.NoActions.class).contains("has no @Action method"));
        }

        @Test
        @DisplayName("both markers on one class")
        void bothMarkers() {
            assertTrue(refusal(Refused.BothMarkers.class).contains("both @Model and @Actions"));
        }

        @Test
        @DisplayName("an abstract @Model")
        void abstractModel() {
            assertTrue(refusal(Refused.Abstract.class).contains("is abstract"));
        }

        @Test
        @DisplayName("a @Model extending a @Model -- the one mistake that would be silent")
        void modelExtendingModel() {
            // Needs the whole tree, not one class: neither Base nor Derived can
            // see the problem alone, which is why the weaver takes the set of
            // every other model being woven.
            var base = ModelWeaver.rewired(Woven.bytesOf(Refused.Base.class));
            var models = java.util.Map.of(
                    Refused.Base.class.getName().replace('.', '/'), base);

            var message = assertThrows(WeaveException.class,
                    () -> ModelWeaver.weave(Woven.bytesOf(Refused.Derived.class), models))
                    .getMessage();

            assertTrue(message.contains("is a @Model too"), message);
            assertTrue(message.contains("notify nobody"), message);
        }

        @Test
        @DisplayName("but a model extending an ordinary class is fine")
        void modelExtendingPlainClass() {
            // Base on its own is a model with an ordinary superclass, which is
            // the common shape and must keep working.
            assertNotNull(ModelWeaver.weave(Woven.bytesOf(Refused.Base.class), java.util.Map.of()));
        }
    }

    @Nested
    @DisplayName("@Actions, the other marker")
    class ActionsMarker {

        // "an @Actions class publishes names and no paths" needs *two* woven
        // classes at once, and `Woven` defines each in a loader of its own -- so
        // the woven Commands would expect the raw Held. That assertion lives in
        // `SplitModelTest` over in `:widgets`, where the build does the weaving.

        @Test
        @DisplayName("and a @Model may still carry actions, for a model too small to split")
        void modelMayStillHaveActions() {
            // The single-class shape stays legal: @Actions is for when the split
            // has happened, not a requirement that it does.
            var model = Model.of(Counter.class);

            assertFalse(model.actions().bound().isEmpty());
            assertFalse(model.bindings().bound().isEmpty());
        }
    }

    @Nested
    @DisplayName("Models, the front door")
    class FrontDoor {

        @Test
        @DisplayName("says which build step did not run")
        void unwovenIsExplained() {
            // The raw class, straight off the test classpath -- annotated and
            // never woven, which is exactly what a misconfigured build produces.
            var raw = new Counter();

            assertFalse(Models.isWoven(raw));
            var message = assertThrows(IllegalStateException.class, () -> Models.bindings(raw))
                    .getMessage();
            assertTrue(message.contains("was not woven"), message);
            assertTrue(message.contains("goldberry.weave"), message);
        }

        @Test
        @DisplayName("says so when the class was never a model at all")
        void notAModelIsExplained() {
            var message = assertThrows(IllegalStateException.class, () -> Models.actions("a string"))
                    .getMessage();
            assertTrue(message.contains("neither @Model nor @Actions"), message);
        }
    }
}
