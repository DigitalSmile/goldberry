package io.github.digitalsmile.goldberry.weaver;

import io.github.digitalsmile.goldberry.bind.Models;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.weaver.models.Clicker;
import io.github.digitalsmile.goldberry.weaver.models.Counter;
import io.github.digitalsmile.goldberry.weaver.models.EightValues;
import io.github.digitalsmile.goldberry.weaver.models.EveryType;
import io.github.digitalsmile.goldberry.weaver.models.OneValue;
import io.github.digitalsmile.goldberry.weaver.models.Split;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/// What the two ways of binding a model cost.
///
/// [ADR-0155](../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)
/// made weaving the **native-image** path and reflection the ordinary jar's, and
/// said the trade was worth it. This is where that claim gets a number instead of
/// an adjective.
///
/// Both arms are in one JVM, on the same model class, measured by one loop:
/// [Woven] weaves the fixture in memory and defines it in a loader of its own,
/// and the raw class is the one on the test classpath. So the comparison is not
/// two numbers taken on different days, and it is not two hand-written imitations
/// of the two schemes — it is the two schemes.
///
/// **Tagged `benchmark`, so `check` never runs it.** Nothing here asserts a
/// timing: a threshold on shared hardware fails for reasons that have nothing to
/// do with the code, and ADR-0045 exists to stop this repository optimising
/// against a number it has not taken. Run with
/// `./gradlew :weaver:benchmark`.
///
/// ## Where the two differ, and where they cannot
///
/// **A read is the same shape either way** — a boxed value out of an
/// [Observable]. Woven it is a `tableswitch` and a `getfield`; bound at run time
/// it is a `VarHandle` read. Neither reflects.
///
/// **A write is where the schemes are not comparable instruction for
/// instruction.** Woven, `clicks++` *is* the notification: the assignment was
/// rewritten into a setter that compares, stores and fires. Bound at run time,
/// `clicks++` is a field increment costing nothing at all, and the notification
/// happens later, in a **sweep** that compares every bound field against what it
/// last held.
///
/// So a benchmark that timed `model.click()` would report the reflective scheme
/// faster, which is true and useless — it would be pricing half of one scheme
/// against all of the other. The honest unit is **a press**: what a
/// `button press="one.click"` causes, from the registry down, sweep included.
/// That is what [#press] measures, and every other measurement here exists to say
/// where the press's time went.
///
/// ## What the sweep is expected to cost, and why it is measured three ways
///
/// A sweep is O(models × bound fields): every field of every model bound at run
/// time, read through a `VarHandle`, boxed, and compared. Three things follow,
/// and each has a measurement:
///
/// - it does not depend on how many fields *changed* ([#sweepWidth]);
/// - it does depend on how many models are attached, because an action sweeps all
///   of them — an `@Actions` record writes to the model beside it, so sweeping
///   only itself would sweep nothing ([#sweepPopulation]);
/// - it is paid per press and per frame, not per write.
///
/// ## Counting the population honestly
///
/// The sweep's cost depends on how many models are bound at run time in this JVM,
/// which is process-wide state. So every model this class creates is kept in
/// [#alive] for the whole run, the methods run in declared order, and each line
/// prints the population it was measured against. Nothing else in the `benchmark`
/// task binds a model, so that count is the population and not an estimate.
@Tag("benchmark")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("woven against bound at run time")
class BindingSchemeBenchmark {

    private static final int WARMUP = 20;
    private static final int RUNS = 200;

    /// How many presses one sample does, so a sample is long enough to time.
    private static final int PRESSES = 100_000;

    /// Every model this class has made, held so that none of them is collected
    /// and the population each line reports is exact.
    private static final List<Object> alive = new ArrayList<>();

    /// How many of them are bound at run time.
    ///
    /// **The number that matters**, and not `alive.size()`: a woven model is not
    /// in the attached map at all, so it is not swept and does not appear in
    /// anybody's press. Counting both together would make every line here look
    /// twice as expensive as it is.
    private static int attached;

    /// Registers a woven model, which nothing sweeps.
    private static <T> T keepWoven(T model) {
        alive.add(model);
        return model;
    }

    /// Registers a model bound at run time, which every press from now on sweeps.
    private static <T> T keepRuntime(T model) {
        alive.add(model);
        attached++;
        return model;
    }

    /// A raw instance of `type`, bound reflectively the moment anything asks.
    private static Clicker raw(Class<? extends Clicker> type) {
        try {
            return keepRuntime(type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /// A woven instance of `type`, held by the interface both forms implement.
    private static Clicker woven(Class<? extends Clicker> type) {
        return keepWoven((Clicker) Woven.instance(type));
    }

    // --- the press, which is the unit ----------------------------------------

    @Test
    @Order(1)
    @DisplayName("a press: what `button press=\"one.click\"` costs, sweep included")
    void press() {
        // **Read the watched pair below, not this one.** With nothing subscribed
        // the woven arm inlines to `clicks++` with two null checks, and a hundred
        // thousand of those in a loop is something the JIT strength-reduces to a
        // single add -- so its number here is not a per-press cost, it is the
        // absence of a loop. The reflective arm cannot be collapsed the same way,
        // because the sweep reads a VarHandle and boxes, so the pair is not a
        // comparison. It is kept because the *runtime* number is meaningful on its
        // own: it is what a press costs when no widget is bound to the value.
        var wovenModel = woven(OneValue.class);
        var runtimeModel = raw(OneValue.class);
        var wovenPress = Models.actions(wovenModel).resolve("one.click");
        var runtimePress = Models.actions(runtimeModel).resolve("one.click");

        report("woven press, no listeners (loop collapses; see below)", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                wovenPress.run();
            }
            return wovenModel.clicks();
        });
        report("runtime press, no listeners", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                runtimePress.run();
            }
            return runtimeModel.clicks();
        });
    }

    @Test
    @Order(2)
    @DisplayName("a press one widget is watching, which is what a bound control is")
    void watchedPress() {
        var wovenModel = woven(OneValue.class);
        var runtimeModel = raw(OneValue.class);
        var sink = new long[1];
        Models.observable(wovenModel, "one.clicks").subscribe(v -> sink[0]++);
        Models.observable(runtimeModel, "one.clicks").subscribe(v -> sink[0]++);
        var wovenPress = Models.actions(wovenModel).resolve("one.click");
        var runtimePress = Models.actions(runtimeModel).resolve("one.click");

        report("woven press, 1 listener", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                wovenPress.run();
            }
            return sink[0];
        });
        report("runtime press, 1 listener", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                runtimePress.run();
            }
            return sink[0];
        });
    }

    // --- where the press's time went -----------------------------------------

    @Test
    @Order(3)
    @DisplayName("the write on its own: a rewritten assignment against a plain one")
    void write() {
        // Not a comparison of two schemes -- it is the same scheme's two halves.
        // The woven number is the whole of its notification; the runtime number is
        // an `iinc` and the notification is in `sweep` below. Reported together so
        // the press above adds up.
        var wovenModel = woven(OneValue.class);
        var runtimeModel = raw(OneValue.class);
        Models.observable(wovenModel, "one.clicks").subscribe(v -> { });
        Models.observable(runtimeModel, "one.clicks").subscribe(v -> { });

        report("woven `clicks++` (compare, store, fire)", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                wovenModel.click();
            }
            return wovenModel.clicks();
        });
        report("runtime `clicks++` (nothing observes it)", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                runtimeModel.click();
            }
            return runtimeModel.clicks();
        });
    }

    @Test
    @Order(4)
    @DisplayName("the sweep on its own, over one model")
    void sweep() {
        var changing = raw(OneValue.class);
        var still = raw(OneValue.class);
        Models.observable(changing, "one.clicks").subscribe(v -> { });
        Models.observable(still, "one.clicks").subscribe(v -> { });
        // `Models.refresh` sweeps the one model named, unlike an action, which
        // sweeps every model there is -- so this is the per-model cost on its own.
        report("refresh, nothing moved", PRESSES, () -> {
            var swept = 0L;
            for (var i = 0; i < PRESSES; i++) {
                swept += Models.refresh(still) ? 1 : 0;
            }
            return swept;
        });
        report("refresh, one field moved", PRESSES, () -> {
            var swept = 0L;
            for (var i = 0; i < PRESSES; i++) {
                changing.click();
                swept += Models.refresh(changing) ? 1 : 0;
            }
            return swept;
        });
        // The control, and the point: a woven model has no sweep to run, so
        // `refresh` looks at it, sees a BoundModel and returns false. Whatever
        // this number is, it is the cost of the call and not of any comparison.
        var wovenControl = woven(OneValue.class);
        report("woven refresh (a no-op: the write already fired)", PRESSES, () -> {
            var swept = 0L;
            for (var i = 0; i < PRESSES; i++) {
                swept += Models.refresh(wovenControl) ? 1 : 0;
            }
            return swept;
        });
    }

    @Test
    @Order(5)
    @DisplayName("a sweep costs what the model is wide, not what changed")
    void sweepWidth() {
        // One field moves in both, and the model that has eight pays for eight:
        // a sweep has no way to know which one moved without reading each. The
        // woven arm is the control -- its setter knows which field it is, so the
        // width does not reach it at all.
        var narrowWoven = woven(OneValue.class);
        var narrowRuntime = raw(OneValue.class);
        var wideWoven = woven(EightValues.class);
        var wideRuntime = raw(EightValues.class);

        press("woven, 1 bound field", narrowWoven, "one.click");
        press("woven, 8 bound fields", wideWoven, "eight.click");
        press("runtime, 1 bound field", narrowRuntime, "one.click");
        press("runtime, 8 bound fields", wideRuntime, "eight.click");
    }

    @Test
    @Order(6)
    @DisplayName("an action sweeps every model, so the population is the other axis")
    void sweepPopulation() {
        // The cost ADR-0155 wrote down and did not measure. Reported as a delta
        // across one phase, so whatever this class attached earlier is a constant
        // in both readings and cancels out of the slope.
        var subject = raw(OneValue.class);
        var press = Models.actions(subject).resolve("one.click");
        var before = attached;

        report("runtime press, " + before + " models swept", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                press.run();
            }
            return subject.clicks();
        });

        for (var i = 0; i < 32; i++) {
            // Bound, not merely constructed: a model nothing has asked about is
            // not attached and is not swept.
            Models.bindings(raw(OneValue.class));
        }
        report("runtime press, " + attached + " models swept", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                press.run();
            }
            return subject.clicks();
        });

        var wovenSubject = woven(OneValue.class);
        var wovenPress = Models.actions(wovenSubject).resolve("one.click");
        report("woven press, " + attached + " models swept (none of them its own)", PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                wovenPress.run();
            }
            return wovenSubject.clicks();
        });
    }

    // --- reading, which is the half that barely differs ----------------------

    @Test
    @Order(7)
    @DisplayName("reading a value through its binding")
    void reads() {
        // Over an array of distinct models, because one observable read in a loop
        // is loop-invariant and the JIT hoists it straight out -- the lesson
        // BindingBenchmark records, and it applies to both arms equally.
        var fanout = 64;
        var wovenValues = new Observable<?>[fanout];
        var runtimeValues = new Observable<?>[fanout];
        for (var i = 0; i < fanout; i++) {
            var wovenModel = woven(OneValue.class);
            var runtimeModel = raw(OneValue.class);
            // Above the Integer cache, so the box is a real allocation rather
            // than a lookup in `Integer.valueOf`'s table.
            for (var n = 0; n < 500 + i; n++) {
                wovenModel.click();
                runtimeModel.click();
            }
            wovenValues[i] = Models.observable(wovenModel, "one.clicks");
            runtimeValues[i] = Models.observable(runtimeModel, "one.clicks");
        }

        report("woven boundValue, int", PRESSES, () -> {
            var total = 0L;
            for (var i = 0; i < PRESSES; i++) {
                total += (Integer) wovenValues[i & 63].get();
            }
            return total;
        });
        report("runtime VarHandle, int", PRESSES, () -> {
            var total = 0L;
            for (var i = 0; i < PRESSES; i++) {
                total += (Integer) runtimeValues[i & 63].get();
            }
            return total;
        });
    }

    // --- what start-up and a document reload pay -----------------------------

    @Test
    @Order(8)
    @DisplayName("building the registries, which is what a document reload does")
    void registryConstruction() {
        var wovenModel = keepWoven(Woven.instance(Counter.class));
        var runtimeModel = keepRuntime(new Counter());

        report("woven bindings() + actions(), x1000", 1_000, () -> {
            var total = 0L;
            for (var i = 0; i < 1_000; i++) {
                total += Models.bindings(wovenModel).bound().size()
                        + Models.actions(wovenModel).bound().size();
            }
            return total;
        });
        report("runtime bindings() + actions(), x1000", 1_000, () -> {
            var total = 0L;
            for (var i = 0; i < 1_000; i++) {
                total += Models.bindings(runtimeModel).bound().size()
                        + Models.actions(runtimeModel).bound().size();
            }
            return total;
        });
    }

    @Test
    @Order(9)
    @DisplayName("binding a class for the first time, which happens once per class")
    void firstBind() {
        // One sample each and no warm-up, because there is no second first time:
        // the reflective plan -- `privateLookupIn`, a `VarHandle` per field, a
        // `MethodHandle` per action -- is computed once per class and cached in a
        // `ClassValue`. A woven class has no plan to compute, so its number is
        // whatever `bindings()` costs cold.
        //
        // Nothing has bound these classes before this line, which is what makes
        // the sample the cold one. Anything that reached them earlier would make
        // this measure the cache.
        System.out.println("  cold, n=1 -- one sample per class, no warm-up:");
        coldBind("runtime EveryType (9 fields, 4 actions)", keepRuntime(new EveryType()));
        coldBind("runtime Split.Values (3 fields, no actions)", keepRuntime(new Split.Values()));
        coldBind("woven   EveryType (9 fields, 4 actions)", keepWoven(Woven.instance(EveryType.class)));
    }

    // --- helpers -------------------------------------------------------------

    /// Times `PRESSES` dispatches of one action, labelled with the population it
    /// was measured against.
    private void press(String what, Clicker model, String action) {
        var run = Models.actions(model).resolve(action);
        report(what, PRESSES, () -> {
            for (var i = 0; i < PRESSES; i++) {
                run.run();
            }
            return model.clicks();
        });
    }

    /// One sample of the first `bindings()` + `actions()` a class ever sees.
    private static void coldBind(String what, Object model) {
        var start = System.nanoTime();
        var bound = Models.bindings(model).bound().size() + Models.actions(model).bound().size();
        var took = System.nanoTime() - start;
        System.out.printf("  %-42s %8.3f us   (%d names)%n", what, took / 1000.0, bound);
    }

    // Deliberately the same shape as BindingBenchmark's, median and mean both,
    // for the reason given there: the JIT and this machine's other tenants skew
    // the mean and the median is what a frame actually experiences. The
    // population is on every line because it is an input to half of these
    // numbers, not a detail of the harness.

    private static void report(String what, long ops, LongSupplier work) {
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

        var median = samples[samples.length / 2] / 1000.0;
        System.out.printf(
                "  %-42s median %8.3f us   mean %8.3f us   p95 %8.3f us"
                        + "   %7.1f ns/op   (swept=%d, sink=%d)%n",
                what,
                median,
                total / (double) RUNS / 1000.0,
                samples[(int) (samples.length * 0.95)] / 1000.0,
                median * 1000.0 / ops,
                attached,
                sink);
    }
}
