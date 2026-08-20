package io.github.digitalsmile.goldberry.weaver;

import io.github.digitalsmile.goldberry.weaver.models.IntProbe;
import io.github.digitalsmile.goldberry.weaver.models.OneValue;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Pricing an option: should a jar **generate** its binding instead of reflecting?
///
/// [ADR-0155](../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)
/// gave an ordinary jar a reflective binding, and `BindingSchemeBenchmark`
/// measured what that costs against the woven form. The obvious next thought is
/// to close the gap without a build step: bind reflectively at start-up, and the
/// first time a model is used, emit a small class with the class-file API and
/// `Lookup::defineHiddenClass` so that every read afterwards is a `getfield`.
///
/// **This file is what decided against it, and the numbers below are the reason
/// the binder is now specialised rather than generated.** Arm 2 is what
/// `RuntimeBinding` does today; when this was written it did arm 1.
///
/// It would work. `privateLookupIn` already gives the private access needed, and
/// a hidden class defined `NESTMATE` may read the model's private fields
/// directly — this file does exactly that and the numbers below are real.
///
/// **The question this exists to answer is narrower than "is generated code
/// faster".** Of course it is. The question is *how much of the reflective
/// number is reflection at all*, because the current path spends some of it on
/// things that have nothing to do with `VarHandle`: an interface hop into
/// `boundValue`, a list lookup by slot, a `WeakReference` deref, and a box on
/// both sides of an `Objects.equals`. If plain specialization recovers most of
/// the gap, generating classes at run time buys the remainder — and costs a
/// bytecode emitter inside `:core`, a third implementation of `BoundModel` to
/// keep in step, and a `defineHiddenClass` call on a path GraalVM has to be told
/// to ignore.
///
/// So four ways of reading one `int` field, in one loop:
///
/// 1. **boxed reflective** — what the sweep did first: `Objects.equals` over two
///    boxes, the value arriving through a `VarHandle` typed as `Object`;
/// 2. **unboxed reflective** — the same `VarHandle`, asked for an `int`. No
///    codegen, no new mechanism, one small class per primitive kind. This is what
///    the sweep does now;
/// 3. **generated nestmate** — a hidden class doing `checkcast`, `getfield`,
///    `ireturn`. The option under consideration;
/// 4. **a plain Java call** — the floor, and what the woven form approaches.
///
/// Plus what generating one costs, since it lands on first use rather than at
/// start-up and a hitch in the first frame is worse than a cost in every one.
///
/// Tagged `benchmark`; `./gradlew :weaver:benchmark`.
@Tag("benchmark")
@DisplayName("reflect, specialize, or generate")
class BindingCodegenBenchmark {

    private static final int WARMUP = 20;
    private static final int RUNS = 200;
    private static final int READS = 100_000;

    /// Distinct models, because one read in a loop is loop-invariant and the JIT
    /// hoists it out — the lesson `BindingBenchmark` records, and it would flatter
    /// every arm here equally and tell us nothing.
    private static final int FANOUT = 64;

    // --- the option, built ---------------------------------------------------

    /// Emits a class that reads `field` off `owner` and returns it unboxed, and
    /// defines it as a **nestmate** of the model.
    ///
    /// Nestmate is the whole trick: it is what lets generated code `getfield` a
    /// private field with no `setAccessible`, no handle and no accessor. It is
    /// also what makes this a real option rather than a sketch — the same
    /// `privateLookupIn` the reflective binder already needs is the one that
    /// grants it.
    private static IntProbe generateProbe(Class<?> owner, String field) {
        var model = owner.describeConstable().orElseThrow();
        // Same package as the lookup class, which `defineHiddenClass` requires.
        var name = ClassDesc.of(owner.getPackageName(), "Probe$" + owner.getSimpleName() + "$" + field);
        var probe = IntProbe.class.describeConstable().orElseThrow();

        var bytes = ClassFile.of().build(name, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            builder.withInterfaceSymbols(probe);
            builder.withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void,
                    ClassFile.ACC_PUBLIC, code -> code
                            .aload(0)
                            .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                    ConstantDescs.MTD_void)
                            .return_());
            builder.withMethodBody("read",
                    MethodTypeDesc.of(ConstantDescs.CD_int, ConstantDescs.CD_Object),
                    ClassFile.ACC_PUBLIC, code -> code
                            .aload(1)
                            .checkcast(model)
                            .getfield(model, field, ConstantDescs.CD_int)
                            .ireturn());
        });
        try {
            var lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            var defined = lookup.defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
            return (IntProbe) defined.lookupClass().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not define the generated probe", e);
        }
    }

    private static VarHandle handleFor(Class<?> owner, String field) {
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
                    .findVarHandle(owner, field, int.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- the measurement -----------------------------------------------------

    @Test
    @DisplayName("reading one int field, four ways")
    void readingOneField() {
        var models = new OneValue[FANOUT];
        for (var i = 0; i < FANOUT; i++) {
            models[i] = new OneValue();
            // Above the Integer cache, so a box is an allocation rather than a
            // lookup in `Integer.valueOf`'s table -- which is the whole cost the
            // unboxed arms are avoiding.
            for (var n = 0; n < 500 + i; n++) {
                models[i].click();
            }
        }
        var handle = handleFor(OneValue.class, "clicks");
        var probe = generateProbe(OneValue.class, "clicks");
        var seen = new Object[FANOUT];
        var seenInts = new int[FANOUT];
        for (var i = 0; i < FANOUT; i++) {
            seen[i] = handle.get(models[i]);
            seenInts[i] = (int) handle.get(models[i]);
        }

        report("1. boxed reflective (what a sweep does now)", () -> {
            var total = 0L;
            for (var i = 0; i < READS; i++) {
                var slot = i & (FANOUT - 1);
                Object now = handle.get(models[slot]);
                total += java.util.Objects.equals(now, seen[slot]) ? 0 : 1;
            }
            return total;
        });
        report("2. unboxed reflective (same VarHandle, no codegen)", () -> {
            var total = 0L;
            for (var i = 0; i < READS; i++) {
                var slot = i & (FANOUT - 1);
                total += (int) handle.get(models[slot]) == seenInts[slot] ? 0 : 1;
            }
            return total;
        });
        report("3. generated nestmate (checkcast, getfield)", () -> {
            var total = 0L;
            for (var i = 0; i < READS; i++) {
                var slot = i & (FANOUT - 1);
                total += probe.read(models[slot]) == seenInts[slot] ? 0 : 1;
            }
            return total;
        });
        report("4. plain Java call (the floor)", () -> {
            var total = 0L;
            for (var i = 0; i < READS; i++) {
                var slot = i & (FANOUT - 1);
                total += models[slot].clicks() == seenInts[slot] ? 0 : 1;
            }
            return total;
        });
    }

    @Test
    @DisplayName("reading the value out, boxed, which is what an Observable hands back")
    void readingThroughAnObservable() {
        // The comparison above is the *sweep's* shape -- read and compare. This
        // one is the *widget's*: `Observable.get()` returns Object, so the box is
        // not optional and the question is only what produces it.
        var models = new OneValue[FANOUT];
        for (var i = 0; i < FANOUT; i++) {
            models[i] = new OneValue();
            for (var n = 0; n < 500 + i; n++) {
                models[i].click();
            }
        }
        var handle = handleFor(OneValue.class, "clicks");
        var probe = generateProbe(OneValue.class, "clicks");

        report("boxed reflective VarHandle.get", () -> {
            var total = 0L;
            for (var i = 0; i < READS; i++) {
                total += (Integer) handle.get(models[i & (FANOUT - 1)]);
            }
            return total;
        });
        // **Read this arm with suspicion.** The box is created and immediately
        // unboxed by `total +=`, so escape analysis deletes it -- which the
        // reflective arm's box, created inside `VarHandle.get`'s own conversion,
        // does not get. A real `Observable.get()` hands the box to a caller that
        // keeps it, so both forms would allocate and the gap would be smaller
        // than this pair says. It is kept because the *reflective* number is
        // sound on its own and is the one the decision leans on.
        report("generated nestmate, boxed at the call site", () -> {
            var total = 0L;
            for (var i = 0; i < READS; i++) {
                total += Integer.valueOf(probe.read(models[i & (FANOUT - 1)]));
            }
            return total;
        });
    }

    @Test
    @DisplayName("what generating one costs, and when it is paid")
    void generationCost() {
        // One class per model per field is the shape that would be needed, and it
        // is paid on first use rather than at start-up -- so it is a hitch in the
        // first frame, not a line in the start-up timeline (ADR-0028).
        var first = System.nanoTime();
        generateProbe(OneValue.class, "clicks");
        var firstTook = System.nanoTime() - first;

        var runs = 200;
        var start = System.nanoTime();
        for (var i = 0; i < runs; i++) {
            generateProbe(OneValue.class, "clicks");
        }
        var warm = (System.nanoTime() - start) / (double) runs;

        System.out.printf("  %-46s %10.3f us   (the first one in the process)%n",
                "generate + defineHiddenClass, cold", firstTook / 1000.0);
        System.out.printf("  %-46s %10.3f us   (n=%d)%n",
                "generate + defineHiddenClass, warm", warm / 1000.0, runs);
    }

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
        var median = samples[samples.length / 2] / 1000.0;
        System.out.printf("  %-46s median %8.3f us   mean %8.3f us   %7.2f ns/op   (sink=%d)%n",
                what, median, total / (double) RUNS / 1000.0, median * 1000.0 / READS, sink);
    }
}
