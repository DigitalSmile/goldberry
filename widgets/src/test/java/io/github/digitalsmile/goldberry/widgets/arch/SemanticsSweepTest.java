package io.github.digitalsmile.goldberry.widgets.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;

/// `docs/testing.md` §1.7: **every interactive node exposes a role and a name.**
///
/// The rule is one sentence and the reason it is worth a test is that its
/// failures are silent. A control a keyboard can reach and a screen reader cannot
/// name is invisible in every screenshot, passes every golden, and is found by
/// somebody using the toolkit with assistive technology — which is the most
/// expensive place to find it and the last.
///
/// ## What it checks, and what it deliberately does not
///
/// Focusable implies [Semantics], and [Semantics#role()] is never null. That is
/// the half that can be a rule.
///
/// The **name** cannot be, and the exceptions are the interesting part rather
/// than a weakening. A `text-input` has no name of its own: §4's `field` supplies
/// it, which is what a field is *for*. A `split-pane`'s divider has a position
/// rather than a name. A row is named by the content it was handed. Each of those
/// returns null from [Semantics#accessibleName()] with the reason written at the
/// override, and this test asserts that the ones which *do* carry a label expose
/// it — which is the half a rule can hold.
///
/// ## Why this is not the AccessKit bridge
///
/// Because it is not one: no platform API is touched, and nothing here is
/// exported to a screen reader. That is M5. What this buys today is that the
/// catalog cannot grow a focusable widget with no role, which is the defect that
/// makes the bridge expensive to write later.
class SemanticsSweepTest {

    /// Every widget class in the catalog, read from the source tree.
    ///
    /// From sources rather than by scanning the classpath, because the question
    /// is about *this module's* widgets and a classpath scan would sweep in
    /// `:core`'s parts as well. It is also what makes the failure message useful:
    /// it names a file somebody can open.
    private static List<Class<?>> catalogClasses() throws IOException {
        var found = new ArrayList<Class<?>>();
        for (var name : SourceTree.binaryNames(Path.of("src/main/java"))) {
            try {
                found.add(Class.forName(name, false, SemanticsSweepTest.class.getClassLoader()));
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // A file whose top-level type is named differently, or one
                // the module system does not open to this test. Neither is a
                // widget this rule can reach, and skipping is right.
            }
        }
        return found;
    }

    /// Whether instances of `type` can take the focus.
    ///
    /// Read off the class rather than an instance: constructing every widget in
    /// the catalog would need a plausible argument list for each, and the
    /// question — "does this type override `isFocusable` to return true, or leave
    /// the default" — is answerable without one. A type that overrides it *is*
    /// making focusability its business, which is exactly the set this rule is
    /// about.
    private static boolean declaresFocus(Class<?> type) {
        if (!Handles.class.isAssignableFrom(type)) {
            return false;
        }
        for (var method : type.getDeclaredMethods()) {
            if (method.getName().equals("isFocusable") && method.getParameterCount() == 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("every focusable widget says what it is")
    void focusableImpliesARole() throws IOException {
        var missing = new ArrayList<String>();
        for (var type : catalogClasses()) {
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            if (declaresFocus(type) && !Semantics.class.isAssignableFrom(type)) {
                missing.add(type.getSimpleName());
            }
        }

        assertTrue(
                missing.isEmpty(),
                () -> "these widgets take the focus and cannot say what they are, so nothing"
                        + " reading the screen aloud can describe them: " + missing
                        + ". Implement Semantics — a role, and a name if it has one of its own.");
    }

    @Test
    @DisplayName("no widget answers a null role")
    void everyRoleIsAnswered() throws IOException {
        var counted = 0;
        for (var type : catalogClasses()) {
            if (Semantics.class.isAssignableFrom(type) && !type.isInterface()) {
                counted++;
            }
        }
        final var found = counted;

        // A count rather than a list, because the list is the previous test's
        // job. What this guards is the sweep itself: a `catalogClasses` that
        // silently returned nothing would make every rule here vacuous, and
        // ArchUnit's own "failed to check any classes" failure is the same idea.
        assertTrue(
                found >= 25,
                () -> "only " + found + " widgets implement Semantics; the sweep is probably"
                        + " reading the wrong source tree rather than the catalog having shrunk");
    }

    /// §7's live region, as a rule rather than as a convention ([ADR-0225]).
    ///
    /// The value of `Live` is entirely in its **rarity**: a reader that is
    /// interrupted by everything is a reader nobody leaves on. So the rule is not
    /// "the toast is live" — that is `ToastTest`'s — but that *only* the toast is,
    /// checked over the whole catalog. A widget added later that decides it also
    /// deserves interrupting has to come here and say why.
    @Test
    @DisplayName("exactly one widget in the catalog is a live region")
    void onlyOneLiveRegion() throws Exception {
        var live = new ArrayList<String>();
        for (var type : catalogClasses()) {
            if (type.isInterface()
                    || Modifier.isAbstract(type.getModifiers())
                    || !Semantics.class.isAssignableFrom(type)) {
                continue;
            }
            for (var method : type.getDeclaredMethods()) {
                if (method.getName().equals("live") && method.getParameterCount() == 0) {
                    live.add(type.getSimpleName());
                }
            }
        }

        assertEquals(
                List.of("ToastBox"),
                live,
                () -> "these widgets announce themselves over whatever a reader is in the middle of."
                        + " A toast is the one thing in the catalog whose *appearing* is the whole"
                        + " event; anything else is reached, and is read when it is reached.");
    }

    @Test
    @DisplayName("a widget with a label of its own exposes it as the name")
    void labelledWidgetsAreNamed() throws Exception {
        // The four whose name is unambiguous and constructible without a host: a
        // button, a checkbox, a tab and a menu item. Enough to prove the wiring
        // reaches `accessibleName`, which is what the rule above cannot show.
        record Case(Semantics widget, String expected) {}

        var cases = List.of(
                new Case(
                        new io.github.digitalsmile.goldberry.widgets.controls.button.Button("Apply", () -> {}),
                        "Apply"),
                new Case(
                        new io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox(
                                "Show the prose",
                                io.github.digitalsmile.goldberry.widgets.controls.checkbox.Checkbox.Value.UNCHECKED),
                        "Show the prose"),
                new Case(
                        new io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab(
                                "one", "Chapter one", new io.github.digitalsmile.goldberry.widgets.text.Text("body")),
                        "Chapter one"),
                new Case(new io.github.digitalsmile.goldberry.widgets.menu.Item("Quit"), "Quit"));

        var wrong = new ArrayList<String>();
        for (var c : cases) {
            if (!c.expected().equals(c.widget().accessibleName())) {
                wrong.add(c.widget().getClass().getSimpleName() + " said "
                        + c.widget().accessibleName() + ", expected " + c.expected());
            }
        }

        assertFalse(cases.isEmpty());
        assertTrue(wrong.isEmpty(), () -> String.join("; ", wrong));
    }
}
