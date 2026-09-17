package io.github.digitalsmile.goldberry.widgets.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// **A widget that is moving has to say so**, and the golden corpus cannot check
/// it ([ADR-0226]).
///
/// A golden drives `render` by hand: it builds a frame, rasterizes it, and
/// compares pixels. It never asks whether the frame loop *would have* asked for
/// the next frame — so a widget that answers `isAnimating()` with `false` while
/// it is fading produces a perfect picture of an animation that never runs. The
/// pictures are right and the application is wrong, and every image in the corpus
/// passes.
///
/// `dialog` shipped with exactly that defect and it was found by running the
/// application ([ADR-0176]). The lesson had nowhere to live but a `TODO.md`
/// entry, which is a note rather than a check. This is the check.
///
/// ## Two rules, and neither is about pixels
///
/// - **A widget that holds a [Phase] declares `isAnimating`.** A phase is the
///   toolkit's word for "arriving or leaving on the frame clock", so holding one
///   and not answering the frame loop is the defect itself, spelled structurally.
/// - **Every declaration of `isAnimating` is asserted about somewhere beside it.**
///   Not every animation is a `Phase` — a tab's transition is a number, a
///   scrollbar's fade is an idle clock — so the second rule catches what the
///   first cannot, by requiring that whoever wrote the method also wrote a test
///   that names it.
///
/// The second rule is deliberately weak about *what* the test asserts: an arch
/// test cannot know a good assertion from a bad one. What it can know is that
/// there is one, which is the difference between this class of bug being caught
/// late by a human and not at all.
class AnimationSweepTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TEST = Path.of("src/test/java");

    /// Every class in the catalog's source tree, by binary name.
    ///
    /// From sources rather than by scanning the classpath, for
    /// [SemanticsSweepTest]'s reason: the question is about *this module's*
    /// widgets, and the failure message should name a file somebody can open.
    private static List<Class<?>> catalogClasses() throws IOException {
        var found = new ArrayList<Class<?>>();
        for (var name : SourceTree.binaryNames(MAIN)) {
            try {
                var type = Class.forName(name, false, AnimationSweepTest.class.getClassLoader());
                found.add(type);
                // Nested types too: `Skeleton` and `CarouselView` both put an
                // animating part inside the widget that owns it, and a sweep
                // that only saw top-level types would miss exactly the parts
                // that move.
                found.addAll(List.of(type.getDeclaredClasses()));
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // A file whose top-level type is named differently, or one
                // the module system does not open to this test.
            }
        }
        return found;
    }

    private static boolean declaresIsAnimating(Class<?> type) {
        for (var method : type.getDeclaredMethods()) {
            if (method.getName().equals("isAnimating") && method.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    /// Whether `type` holds a phase — directly, or inside a value of its own that
    /// does.
    ///
    /// One level of nesting, which is what the catalog has:
    /// `ToastBox.Reflow` is a journey with a phase in it, and a toast that held
    /// only a reflow would still be moving. Deeper than that is not a shape
    /// anything here uses, and a general graph walk would be machinery for a case
    /// that does not exist.
    private static boolean holdsAPhase(Class<?> type) {
        for (var field : type.getDeclaredFields()) {
            if (field.getType() == Phase.class) {
                return true;
            }
            for (var nested : field.getType().getDeclaredFields()) {
                if (nested.getType() == Phase.class) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("a widget that holds a phase says it is animating")
    void aPhaseImpliesADeclaration() throws IOException {
        var silent = new ArrayList<String>();
        for (var type : catalogClasses()) {
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            // **Widgets only.** A `State` and a value record may both hold a
            // phase — a dialog's `closing`, a toast's `Reflow` — and neither is
            // asked for the next frame: the frame loop walks the *element* tree
            // and asks the widgets in it. Holding a phase somewhere that is not
            // painted is how a phase gets to a widget, not a defect.
            if (Paints.class.isAssignableFrom(type) && holdsAPhase(type) && !declaresIsAnimating(type)) {
                silent.add(type.getSimpleName());
            }
        }

        assertTrue(
                silent.isEmpty(),
                () -> "these widgets are on their way in or out and never ask for the next frame,"
                        + " so they will be painted once at whatever the loop caught and left there: "
                        + silent + ". Override isAnimating() and answer the phase.");
    }

    /// The rule that catches the animations a `Phase` does not describe.
    ///
    /// A test **beside** the widget rather than anywhere in the corpus: the
    /// package is what makes the search meaningful, and a widget whose only
    /// mention of `isAnimating` is in some distant integration test is a widget
    /// whose author did not think about it.
    @Test
    @DisplayName("every isAnimating in the catalog is asserted about in its own package")
    void everyDeclarationIsAsserted() throws IOException {
        var declaring = new LinkedHashMap<String, List<String>>();
        for (var type : catalogClasses()) {
            if (!type.isInterface() && declaresIsAnimating(type)) {
                declaring
                        .computeIfAbsent(type.getPackageName(), unused -> new ArrayList<>())
                        .add(type.getSimpleName());
            }
        }

        var unchecked = new ArrayList<String>();
        for (var entry : declaring.entrySet()) {
            if (!packageAsserts(entry.getKey())) {
                unchecked.add(entry.getKey() + " (" + String.join(", ", entry.getValue()) + ")");
            }
        }

        assertTrue(
                !declaring.isEmpty(),
                "no widget in the catalog declares isAnimating, which means this sweep is reading"
                        + " the wrong source tree rather than the catalog having stopped moving");
        assertTrue(
                unchecked.isEmpty(),
                () -> "these packages animate and no test beside them names isAnimating, so a"
                        + " widget that stopped asking for frames would still pass every golden: "
                        + unchecked);
    }

    /// Whether any test source in `packageName` mentions `isAnimating`.
    private static boolean packageAsserts(String packageName) throws IOException {
        var dir = TEST.resolve(packageName.replace('.', '/'));
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var files = Files.list(dir)) {
            for (var file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("isAnimating")) {
                    return true;
                }
            }
        }
        return false;
    }
}
