package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.BindingRegistry;
import io.github.digitalsmile.goldberry.bind.Model;
import io.github.digitalsmile.goldberry.bind.Models;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Property;
import java.util.Arrays;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// What the binding schema costs, before and after ADR-0125.
///
/// The "before" is not a description — it is [Old] below, which is the scheme the
/// repository actually shipped: a `Property` per value, `set(get() + 1)` to
/// change one, and a registry of method references of the kind the annotation
/// processor generated. The "after" is [New], the same model written as plain
/// fields. Both are here, in one file, measured by one loop, so the comparison is
/// not two numbers taken on different days.
///
/// ## Which "after" this measures depends on the build
///
/// Since [ADR-0155] a `@Model` is bound one of two ways, and this module does not
/// weave — so what runs here is normally the **reflective** binding, which is what
/// an application's own jar uses. Every label below says which form it got rather
/// than assuming, because a benchmark that names a mechanism it did not measure is
/// worse than one that prints no label at all.
///
/// The two forms against **each other** are `BindingSchemeBenchmark` in `:weaver`,
/// which is the only module that can hold both at once.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing: a threshold on shared hardware fails for reasons that have nothing to
/// do with the code, and ADR-0045 exists to stop this repository optimising
/// against a number it has not taken. Run with
/// `./gradlew :example:benchmark --tests '*BindingBenchmark*'`.
///
/// ## Every write here is a press, and that is not a detail
///
/// `old.click()` notifies, because the `Property` inside it does. `plain.click()`
/// is an `iinc` and notifies nobody until something sweeps — which is the whole
/// shape of ADR-0155, and it means a benchmark timing the two *methods* would be
/// pricing all of one scheme against a fraction of the other and calling the
/// difference a speed-up.
///
/// So every write below is dispatched through the action registry, which is what a
/// `button press=` does and what both schemes share. Whatever the second arm's
/// notification costs — a rewritten setter or a sweep — it is inside the number.
///
/// ## What to expect, and why
///
/// `Property.set` is a virtual call, an `Objects.equals` on two boxes, and — for
/// an `int` — a `valueOf` on the way in whether or not anybody is listening. A
/// woven setter compares two `int`s with `if_icmpne` and boxes only once it knows
/// the value changed. A sweep reads each field through a `VarHandle`, boxes it and
/// compares, once per press, for every model bound at run time.
///
/// Reads are their own trade: both of the new forms box where `Property.get` is
/// one `getfield`. That is what ADR-0125 bought — writes are what a model does on
/// every event, and reads through a binding happen once per rebuild.
@Tag("benchmark")
@DisplayName("the binding schema, before and after")
class BindingBenchmark {

    private static final int WARMUP = 20;
    private static final int RUNS = 200;

    /// What to call the second scheme in this run's output — `woven` when the
    /// build ran the weaver over this module, `runtime` when it did not
    /// ([ADR-0155](../../../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)).
    private static final String FORM = Models.isWoven(new New()) ? "woven" : "runtime";

    /// How many writes one sample does, so a sample is long enough to time.
    private static final int WRITES = 100_000;

    // --- the two schemes -----------------------------------------------------

    /// The scheme this replaced: one `Property` per value.
    ///
    /// Registered exactly the way the generated `…Registry` did it — a strict
    /// registry, `bind(path, property)` per field and `bind(name, target::method)`
    /// per action, which is a method reference and therefore already a
    /// `LambdaMetafactory` call site. That half did not get faster and was never
    /// the problem.
    static final class Old {

        private final Property<Integer> clicks = Property.of(0);
        private final Property<String> label = Property.of("idle");
        private final Property<Boolean> on = Property.of(false);
        private final Property<Number> gain = Property.of(40);

        void click() {
            clicks.set(clicks.get() + 1);
        }

        void say(String text) {
            label.set(text);
        }

        void setGain(double value) {
            gain.set(value);
        }

        void toggle() {
            on.set(!Boolean.TRUE.equals(on.get()));
        }

        /// Changes nothing, so a loop over it measures the call site and not the
        /// model behind it.
        void noop() {
        }

        BindingRegistry bindings() {
            return BindingRegistry.strict()
                    .bind("app.clicks", clicks)
                    .bind("app.label", label)
                    .bind("app.on", on)
                    .bind("app.gain", gain);
        }

        ActionRegistry actions() {
            return ActionRegistry.strict()
                    .bind("app.click", this::click)
                    .bind("app.noop", this::noop)
                    .bind("app.toggle", this::toggle)
                    .bind("app.say", (java.util.function.Consumer<String>) this::say)
                    .bind("app.set-gain", value -> setGain(Double.parseDouble(value)));
        }
    }

    /// The same model, written as plain fields.
    ///
    /// Nested in a test class, so whatever this build does to a compiled model it
    /// does to this one — which is how the labels below stay true without being
    /// told which build they are in.
    @Model
    static final class New {

        @Bind("app.clicks")
        private int clicks;
        @Bind("app.label")
        private String label = "idle";
        @Bind("app.on")
        private boolean on;
        @Bind("app.gain")
        private Number gain = 40;

        @Action("app.click")
        void click() {
            clicks++;
        }

        @Action("app.say")
        void say(String text) {
            label = text;
        }

        @Action("app.set-gain")
        void setGain(double value) {
            gain = value;
        }

        @Action("app.toggle")
        void toggle() {
            on = !on;
        }

        /// Changes nothing, so a loop over it measures the call site and not the
        /// model behind it.
        @Action("app.noop")
        void noop() {
        }
    }

    // --- the measurements ----------------------------------------------------

    @Test
    @DisplayName("changing a value nobody is watching")
    void unwatchedWrites() {
        var old = new Old();
        var plain = new New();
        var oldPress = old.actions().resolve("app.click");
        var newPress = Models.actions(plain).resolve("app.click");

        report("Property.set, no listeners", () -> {
            for (var i = 0; i < WRITES; i++) {
                oldPress.run();
            }
            return old.clicks.get();
        });
        report(FORM + " field, no listeners", () -> {
            for (var i = 0; i < WRITES; i++) {
                newPress.run();
            }
            return plain.clicks;
        });
    }

    @Test
    @DisplayName("changing a value one widget is watching")
    void watchedWrites() {
        var old = new Old();
        var plain = new New();
        var sink = new long[1];

        old.bindings().resolve("app.clicks").subscribe(v -> sink[0]++);
        Models.bindings(plain).resolve("app.clicks").subscribe(v -> sink[0]++);
        var oldPress = old.actions().resolve("app.click");
        var newPress = Models.actions(plain).resolve("app.click");

        report("Property.set, 1 listener", () -> {
            for (var i = 0; i < WRITES; i++) {
                oldPress.run();
            }
            return sink[0];
        });
        report(FORM + " field, 1 listener", () -> {
            for (var i = 0; i < WRITES; i++) {
                newPress.run();
            }
            return sink[0];
        });
    }

    @Test
    @DisplayName("changing a value that has not changed")
    void unchangedWrites() {
        // The case a real application hits constantly: a model recomputes a
        // status line and assigns the same string back. Both schemes stop, and
        // the question is what the stopping costs.
        var old = new Old();
        var plain = new New();
        var oldSay = old.actions().resolveValued("app.say");
        var newSay = Models.actions(plain).resolveValued("app.say");
        oldSay.accept("steady");
        newSay.accept("steady");

        report("Property.set, same value", () -> {
            for (var i = 0; i < WRITES; i++) {
                oldSay.accept("steady");
            }
            return old.label.get().length();
        });
        report(FORM + " field, same value", () -> {
            for (var i = 0; i < WRITES; i++) {
                newSay.accept("steady");
            }
            return plain.label.length();
        });
    }

    @Test
    @DisplayName("reading a value through its binding")
    void reads() {
        // Over an array of distinct models, because one observable read in a
        // loop is loop-invariant and the JIT hoists it straight out -- which is
        // how a first cut of this measured `Property.get` at 0.015ns and made
        // the woven read look 70x worse than it is.
        var fanout = 64;
        var olds = new Observable<?>[fanout];
        var news = new Observable<?>[fanout];
        var oldLabels = new Observable<?>[fanout];
        var newLabels = new Observable<?>[fanout];
        for (var i = 0; i < fanout; i++) {
            var old = new Old();
            var woven = new New();
            // Above the Integer cache, so the box is a real allocation rather
            // than a lookup in `Integer.valueOf`'s table.
            for (var n = 0; n < 500 + i; n++) {
                old.click();
                woven.click();
            }
            olds[i] = old.bindings().resolve("app.clicks");
            news[i] = Models.bindings(woven).resolve("app.clicks");
            oldLabels[i] = old.bindings().resolve("app.label");
            newLabels[i] = Models.bindings(woven).resolve("app.label");
        }

        report("Property.get, int", () -> {
            var total = 0L;
            for (var i = 0; i < WRITES; i++) {
                total += (Integer) olds[i & 63].get();
            }
            return total;
        });
        report(FORM + " read, int (boxes)", () -> {
            var total = 0L;
            for (var i = 0; i < WRITES; i++) {
                total += (Integer) news[i & 63].get();
            }
            return total;
        });
        report("Property.get, reference", () -> {
            var total = 0L;
            for (var i = 0; i < WRITES; i++) {
                total += ((String) oldLabels[i & 63].get()).length();
            }
            return total;
        });
        report(FORM + " read, reference", () -> {
            var total = 0L;
            for (var i = 0; i < WRITES; i++) {
                total += ((String) newLabels[i & 63].get()).length();
            }
            return total;
        });
    }

    @Test
    @DisplayName("dispatching an action, with nothing behind it")
    void actionDispatch() {
        // Against a method that does nothing, because the two schemes bind
        // identically -- both are a LambdaMetafactory call site -- and any
        // difference here would be measurement error. A first cut of this
        // measured `app.click` on both models and reported the woven form 5.8x
        // faster, which was the *write* path showing up again under an
        // "action dispatch" label.
        var old = new Old();
        var woven = new New();
        var oldRun = old.actions().resolve("app.noop");
        var newRun = Models.actions(woven).resolve("app.noop");

        report("method reference -> Runnable.run", () -> {
            for (var i = 0; i < WRITES; i++) {
                oldRun.run();
            }
            return 0L;
        });
        report(FORM + " dispatch -> Runnable.run", () -> {
            for (var i = 0; i < WRITES; i++) {
                newRun.run();
            }
            return 0L;
        });
    }

    @Test
    @DisplayName("what markup actually causes: an action, and the change it makes")
    void actionEndToEnd() {
        var old = new Old();
        var woven = new New();
        var oldActions = old.actions();
        var newActions = Models.actions(woven);
        var oldRun = oldActions.resolve("app.click");
        var newRun = newActions.resolve("app.click");
        var oldValued = oldActions.resolveValued("app.set-gain");
        var newValued = newActions.resolveValued("app.set-gain");

        report("press -> Property.set", () -> {
            for (var i = 0; i < WRITES; i++) {
                oldRun.run();
            }
            return old.clicks.get();
        });
        report("press -> " + FORM + " field", () -> {
            for (var i = 0; i < WRITES; i++) {
                newRun.run();
            }
            return woven.clicks;
        });
        // Both dominated by Double.parseDouble, which is the point: the parse a
        // valued action needs is the same parse either way, and it costs more
        // than everything around it.
        report("change -> generated parse -> set", () -> {
            for (var i = 0; i < WRITES; i++) {
                oldValued.accept("62.5");
            }
            return old.gain.get().longValue();
        });
        report("change -> " + FORM + " parse -> field", () -> {
            for (var i = 0; i < WRITES; i++) {
                newValued.accept("62.5");
            }
            return woven.gain.longValue();
        });
    }

    @Test
    @DisplayName("building the registries, which is what a document reload does")
    void registryConstruction() {
        var old = new Old();
        var woven = new New();

        report("Old.bindings() + actions(), x1000", () -> {
            var total = 0L;
            for (var i = 0; i < 1_000; i++) {
                total += old.bindings().bound().size() + old.actions().bound().size();
            }
            return total;
        });
        report(FORM + " bindings() + actions(), x1000", () -> {
            var total = 0L;
            for (var i = 0; i < 1_000; i++) {
                total += Models.bindings(woven).bound().size() + Models.actions(woven).bound().size();
            }
            return total;
        });
    }

    @Test
    @DisplayName("allocating the model itself")
    void allocation() {
        // Four Properties per model against none: the woven model's values live
        // in the fields it already had, and its listener store is not created
        // until something subscribes.
        //
        // Parked in an array that outlives the loop, because escape analysis
        // deletes an allocation nothing can observe -- a first cut of this timed
        // 10,000 `new Old()` at 66ns total, which is not allocation, it is the
        // JIT noticing there was none.
        var parked = new Object[1024];

        report("new Old(), x10000", () -> {
            var total = 0L;
            for (var i = 0; i < 10_000; i++) {
                var made = new Old();
                parked[i & 1023] = made;
                total += made.clicks.get();
            }
            return total;
        });
        report("new New(), x10000", () -> {
            var total = 0L;
            for (var i = 0; i < 10_000; i++) {
                var made = new New();
                parked[i & 1023] = made;
                total += made.clicks;
            }
            return total;
        });
    }

    @Test
    @DisplayName("the showcase's own model, end to end")
    void showcaseModel() {
        var model = new ShowcaseModel();
        var actions = Models.actions(model);
        var click = actions.resolve("app.click");
        Models.bindings(model).resolve("app.clicks").subscribe(v -> { });

        report("ShowcaseModel click, 1 listener", () -> {
            for (var i = 0; i < WRITES; i++) {
                click.run();
            }
            return (Integer) Models.observable(model, "app.clicks").get();
        });
    }

    // --- reporting -----------------------------------------------------------
    //
    // Deliberately the same shape as FrameBenchmark's and TextBenchmark's, median
    // and mean both, for the reason given there: the JIT and this machine's other
    // tenants skew the mean and the median is what a frame actually experiences.

    private static void report(String what, LongSupplier work) {
        var sink = 0L;
        for (var i = 0; i < WARMUP; i++) {
            sink += work.getAsLong();
        }

        var samples = new long[RUNS];
        for (var i = 0; i < RUNS; i++) {
            var start = System.nanoTime();
            sink += work.getAsLong();
            samples[i] = System.nanoTime() - start;
        }

        var total = 0L;
        for (var sample : samples) {
            total += sample;
        }
        Arrays.sort(samples);

        System.out.printf(
                "  %-42s median %8.3f us   mean %8.3f us   p95 %8.3f us   (n=%d, sink=%d)%n",
                what,
                samples[samples.length / 2] / 1000.0,
                total / (double) RUNS / 1000.0,
                samples[(int) (samples.length * 0.95)] / 1000.0,
                RUNS,
                sink);
    }
}
