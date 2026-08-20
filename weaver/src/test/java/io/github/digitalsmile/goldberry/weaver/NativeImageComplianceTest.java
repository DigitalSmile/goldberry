package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.weaver.models.Counter;
import io.github.digitalsmile.goldberry.weaver.models.EveryType;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a closed world can and cannot link, asserted against the woven bytecode.
///
/// GraalVM's native image has no runtime class loading, no runtime class
/// generation, and no reflection it was not told about at build time. The claim
/// ADR-0127 makes is that the binding schema needs none of the three — and this
/// is that claim as a test rather than as a sentence, because "we did not use
/// reflection" is exactly the kind of thing that stops being true one commit
/// after somebody writes it down.
///
/// ## What this does not prove
///
/// It does not prove the *toolkit* builds as a native image; that involves SDL3,
/// Blend2D and a pile of FFM downcalls, and no image has been built in this
/// repository yet. It proves the thing this change is responsible for: that
/// nothing in a woven model, or on the path from a markup name to the method it
/// names, requires the open world.
@DisplayName("what a closed world can link")
class NativeImageComplianceTest {

    /// Methods that mean "this needs the open world".
    ///
    /// Every one of them either loads a class by name, generates one, or reaches
    /// a member the image builder was not told about. `privateLookupIn` and
    /// `findVarHandle` are on the list for a pointed reason: they are what the
    /// **previous** scheme's generated registry ran in its static initializer
    /// (ADR-0098), and they are the reason it needed reachability metadata that
    /// this one does not.
    private static final Set<String> CLOSED_WORLD_HOSTILE = Set.of(
            "java/lang/Class.forName",
            "java/lang/Class.getDeclaredField",
            "java/lang/Class.getDeclaredMethod",
            "java/lang/Class.getField",
            "java/lang/Class.getMethod",
            "java/lang/Class.newInstance",
            "java/lang/reflect/AccessibleObject.setAccessible",
            "java/lang/reflect/Method.invoke",
            "java/lang/reflect/Field.get",
            "java/lang/reflect/Field.set",
            "java/lang/invoke/MethodHandles.privateLookupIn",
            "java/lang/invoke/MethodHandles$Lookup.findVarHandle",
            "java/lang/invoke/MethodHandles$Lookup.findVirtual",
            "java/lang/invoke/MethodHandles$Lookup.findStatic",
            "java/lang/invoke/MethodHandles$Lookup.defineClass",
            "java/lang/invoke/MethodHandles$Lookup.defineHiddenClass",
            "java/lang/invoke/LambdaMetafactory.metafactory",
            "java/lang/ClassLoader.defineClass");

    /// Every method a class calls, as `owner.name`.
    private static List<String> callsIn(byte[] woven) {
        var calls = new ArrayList<String>();
        for (var method : ClassFile.of().parse(woven).methods()) {
            method.code().ifPresent(code -> {
                for (var element : code) {
                    if (element instanceof InvokeInstruction invoke) {
                        calls.add(invoke.owner().asInternalName() + "." + invoke.name().stringValue());
                    }
                }
            });
        }
        return calls;
    }

    /// Every `invokedynamic` bootstrap a class uses, as `owner.name`.
    private static List<String> bootstrapsIn(byte[] woven) {
        return bootstrapsIn(woven, name -> true);
    }

    /// The same, for the methods whose names `wanted` accepts.
    ///
    /// Needed because a model's own code may contain lambdas of its own —
    /// `Counter.click` assigns through a `Runnable` — and those are `LambdaMeta`
    /// `factory` call sites too. Counting the ones the weaver wrote means looking
    /// only at the method the weaver wrote them in.
    private static List<String> bootstrapsIn(byte[] woven, java.util.function.Predicate<String> wanted) {
        var bootstraps = new ArrayList<String>();
        for (var method : ClassFile.of().parse(woven).methods()) {
            if (!wanted.test(method.methodName().stringValue())) {
                continue;
            }
            method.code().ifPresent(code -> {
                for (var element : code) {
                    if (element instanceof InvokeDynamicInstruction indy) {
                        var bootstrap = indy.bootstrapMethod();
                        bootstraps.add(bootstrap.owner().displayName() + "." + bootstrap.methodName());
                    }
                }
            });
        }
        return bootstraps;
    }

    @Test
    @DisplayName("a woven model calls nothing that needs the open world")
    void nothingHostile() {
        for (var type : List.of(Counter.class, EveryType.class)) {
            var calls = callsIn(Woven.weave(type));
            for (var call : calls) {
                assertFalse(CLOSED_WORLD_HOSTILE.contains(call),
                        type.getSimpleName() + " calls " + call
                                + ", which a closed world cannot link without being told about it");
            }
        }
    }

    @Test
    @DisplayName("every call site is bootstrapped by LambdaMetafactory, which the image builder resolves")
    void onlyLambdaMetafactory() {
        // The distinction that makes this work. `LambdaMetafactory.metafactory`
        // appearing in the hostile list above and as the *only* permitted
        // bootstrap here is not a contradiction: calling it is spinning a class
        // at runtime, and naming it as an `invokedynamic` bootstrap is asking the
        // linker for a call site -- which native image resolves when it builds
        // the image, exactly as it does for every method reference javac emits.
        var bootstraps = bootstrapsIn(Woven.weave(Counter.class));

        assertFalse(bootstraps.isEmpty(), "the model has actions, so it has call sites");
        for (var bootstrap : bootstraps) {
            assertEquals("LambdaMetafactory.metafactory", bootstrap);
        }
    }

    @Test
    @DisplayName("there is one call site per action, and no more")
    void oneCallSitePerAction() {
        // Nine @Action methods on Counter, so nine call sites in `actions()`.
        // A call site that appeared twice would mean the image carried two
        // generated lambda classes for one handler.
        assertEquals(9, bootstrapsIn(Woven.weave(Counter.class), "actions"::equals).size());
    }

    @Test
    @DisplayName("the runtime the woven code calls is ordinary code")
    void runtimeIsOrdinary() {
        // FieldListeners, BoundField, BindingRegistry and ActionRegistry are plain classes with
        // plain methods -- the woven model reaches them by `invokevirtual` and
        // `invokestatic`, which is the whole point. If this ever fails it is
        // because somebody put a lookup or a proxy behind one of them.
        for (var runtime : List.of(
                io.github.digitalsmile.goldberry.bind.FieldListeners.class,
                io.github.digitalsmile.goldberry.bind.BoundField.class,
                io.github.digitalsmile.goldberry.bind.BindingRegistry.class,
                io.github.digitalsmile.goldberry.bind.ActionRegistry.class,
                io.github.digitalsmile.goldberry.bind.Models.class)) {

            for (var call : callsIn(Woven.bytesOf(runtime))) {
                assertFalse(CLOSED_WORLD_HOSTILE.contains(call),
                        runtime.getSimpleName() + " calls " + call);
            }
        }
    }

    @Test
    @DisplayName("a model keeps its annotations at runtime, and an image reads none of them")
    void annotationRetention() {
        // All of them are RUNTIME-retained since ADR-0155, because that is what
        // an unwoven jar binds from: the weaver is the native-image path and the
        // reflective binder is the ordinary one, and the reflective binder cannot
        // read a CLASS-retained annotation at all.
        //
        // The cost in an image is the annotation metadata itself, which nothing
        // there reads -- a woven model's registries are code, and this whole
        // class is the assertion that they are. That is a few bytes per member
        // against a build step every consumer would otherwise have to install,
        // and the trade is recorded in ADR-0155.
        assertTrue(Counter.class.isAnnotationPresent(
                io.github.digitalsmile.goldberry.bind.Model.class));
        // Counted against the woven registries rather than against a literal, so
        // this stays true when somebody adds a field to Counter: what it asserts
        // is that reflection sees exactly what the weaver saw.
        var woven = Woven.instance(Counter.class);
        assertEquals(io.github.digitalsmile.goldberry.bind.Models.bindings(woven).bound().size(),
                java.util.Arrays.stream(Counter.class.getDeclaredFields())
                        .filter(f -> f.isAnnotationPresent(
                                io.github.digitalsmile.goldberry.bind.Bind.class))
                        .count(),
                "every @Bind field is still readable at run time");
        assertEquals(io.github.digitalsmile.goldberry.bind.Models.actions(woven).bound().size(),
                java.util.Arrays.stream(Counter.class.getDeclaredMethods())
                        .filter(m -> m.isAnnotationPresent(
                                io.github.digitalsmile.goldberry.bind.Action.class))
                        .count(),
                "every @Action method is still readable at run time");
    }

    @Test
    @DisplayName("the woven form is what an image gets, and it reads no annotation to do it")
    void wovenReadsNoAnnotation() {
        // The claim the paragraph above trades against: whatever metadata the
        // class carries, the woven registries do not consult it. `bindings()` and
        // `actions()` are emitted code over emitted constants, so an image that
        // dropped every annotation would behave identically.
        var woven = Woven.of(Counter.class);
        for (var call : callsIn(Woven.weave(Counter.class))) {
            assertFalse(call.contains("getAnnotation") || call.contains("isAnnotationPresent")
                            || call.contains("getDeclaredAnnotation"),
                    woven.getName() + " reads an annotation at run time: " + call);
        }
    }
}
