package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.bind.BindingRegistry;
import io.github.digitalsmile.goldberry.bind.Models;
import io.github.digitalsmile.goldberry.weaver.models.Counter;
import io.github.digitalsmile.goldberry.weaver.models.EveryType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The two forms of a binding, held to the same answers.
///
/// Since [ADR-0155] a model is bound one of two ways: the weaver rewrites the
/// class for a native image, and an ordinary jar reads the same annotations at
/// run time. Two mechanisms with nothing in common — a rewritten `putfield`
/// against a `VarHandle` and a sweep — and the whole promise of the arrangement
/// is that an application cannot tell which one it got.
///
/// So this is the test that matters most of the two suites either side of it: the
/// same model class, once raw and once woven, driven through the same actions,
/// asserted to publish the same paths, the same names, the same values and the
/// same notifications in the same order.
///
/// `:weaver` does not apply `goldberry.weave` to itself, so the classes under
/// `models/` are raw as javac left them and [Woven] produces the other side.
@DisplayName("a model bound at run time and the same model woven")
class RuntimeAgreesWithWovenTest {

    /// One model under test, with everything it reported written down.
    private record Bound(Object instance, BindingRegistry bindings, ActionRegistry actions,
            Map<String, List<Object>> reported, List<String> asked) {

        static Bound of(Object instance) {
            var bindings = Models.bindings(instance);
            var reported = new LinkedHashMap<String, List<Object>>();
            for (var path : bindings.bound().keySet()) {
                var seen = new ArrayList<>();
                reported.put(path, seen);
                bindings.resolve(path).subscribe(seen::add);
            }
            var asked = new ArrayList<String>();
            Models.onRestyle(instance, () -> asked.add("restyle"));
            Models.onRepaint(instance, () -> asked.add("frame"));
            return new Bound(instance, bindings, Models.actions(instance), reported, asked);
        }

        void press(String name) {
            actions.resolve(name).run();
        }

        void send(String name, String value) {
            actions.resolveValued(name).accept(value);
        }

        /// What every path holds now, by name — the state of the model as the
        /// binding half of it can see it.
        Map<String, Object> values() {
            var values = new LinkedHashMap<String, Object>();
            for (var path : bindings.bound().keySet()) {
                values.put(path, bindings.resolve(path).get());
            }
            return values;
        }
    }

    /// The same class either side: raw on the left, woven on the right.
    private record Pair(Bound runtime, Bound woven) {

        static Pair of(Class<?> type) {
            return new Pair(Bound.of(instance(type)), Bound.of(Woven.instance(type)));
        }

        private static Object instance(Class<?> type) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("could not instantiate " + type.getName(), e);
            }
        }

        void press(String name) {
            runtime.press(name);
            woven.press(name);
        }

        void send(String name, String value) {
            runtime.send(name, value);
            woven.send(name, value);
        }

        /// Everything the two are asked to agree on, in one place so a failure
        /// says which of the four disagreed.
        void agree() {
            assertFalse(Models.isWoven(runtime.instance()),
                    "the left-hand model should be the raw one");
            assertTrue(Models.isWoven(woven.instance()),
                    "the right-hand model should be the woven one");

            // As sets, and this is the one thing the two forms do not promise to
            // agree on: the weaver publishes in class-file order, and reflection
            // has no way to recover that -- `getDeclaredFields` and
            // `getDeclaredMethods` promise no order at all -- so the runtime form
            // sorts by member name instead of leaving it to the JVM. What is
            // asserted is that the same names are there.
            assertEquals(woven.bindings().bound().keySet(),
                    runtime.bindings().bound().keySet(), "the paths");
            assertEquals(woven.actions().bound().keySet(),
                    runtime.actions().bound().keySet(), "the action names");
            assertEquals(woven.values(), runtime.values(), "the value behind every path");
            // Order-independent by construction: a Map compares by entry, so this
            // is per path rather than per notification across the model.
            assertEquals(woven.reported(), runtime.reported(),
                    "what each path reported, in order");
            assertEquals(woven.asked(), runtime.asked(),
                    "the restyles and the frames, in order");
        }
    }

    @Test
    @DisplayName("agree on a model of the usual shape, through every one of its actions")
    void counter() {
        var pair = Pair.of(Counter.class);

        // Including the writes that change nothing: "fired once per change and
        // not once per write" is a rule both halves have to keep, and it is the
        // one a sweep could most easily get wrong.
        pair.press("app.click");
        pair.press("app.click");
        pair.send("app.say", "hello");
        pair.send("app.say", "hello");
        pair.press("app.reset");
        pair.press("app.reset");
        pair.send("app.set-gain", "2.5");
        pair.send("app.set-clicks", "9");
        pair.send("app.set-on", "true");
        pair.press("app.tick");
        pair.send("app.pick-theme", "light");

        pair.agree();
    }

    @Test
    @DisplayName("agree on every field shape, so the boxing and the comparison match")
    void everyType() {
        var pair = Pair.of(EveryType.class);

        pair.press("t.set");
        pair.press("t.set");
        // `Double.compare(NaN, NaN) == 0`, which is what `Objects.equals` says
        // too -- so the second of these is silent on both sides or neither.
        pair.press("t.nan");
        pair.press("t.nan");
        pair.press("t.negativeZero");

        pair.agree();
    }

    @Test
    @DisplayName("agree that an action returning a value is called for its effect")
    void returningAction() {
        var pair = Pair.of(Counter.class);

        pair.press("app.bump");
        pair.press("app.bump");

        pair.agree();
    }

    @Test
    @DisplayName("agree on a Property field, which neither of them rewires")
    void propertyField() {
        var pair = Pair.of(Counter.class);

        assertEquals("outside", pair.runtime().bindings().resolve("app.owned").get());
        assertEquals("outside", pair.woven().bindings().resolve("app.owned").get());
        pair.agree();
    }
}
