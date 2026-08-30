package io.github.digitalsmile.goldberry.widgets.panel.list;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.panel.Described;

/// The typeahead **timeout**, which nothing could assert until the clock became
/// the host's.
///
/// A list decides whether the letter that just arrived continues the previous
/// search or begins a new one by measuring the gap between them. That gap used to
/// be measured against `System.currentTimeMillis()`, and the consequence was not
/// that the behaviour was wrong — it was that the only way to test it was to
/// sleep for a second and hope the machine was not busy, so nobody did. Every
/// existing typeahead test types letters back to back and asserts the
/// *accumulating* half; the expiring half went untested for as long as it has
/// existed.
///
/// `Host.clock()` is what changed. `TestHost` hands out a
/// [io.github.digitalsmile.goldberry.motion.Clock#virtual()], so a second of
/// silence is `advance(1001)` and the test runs in microseconds
/// (`docs/testing.md` §0.1: determinism is a feature under test, and anything
/// that cannot be driven is a bug rather than a limitation).
class TypeaheadClockTest {

    /// Longer than `ListState.TYPEAHEAD_MILLIS`, which is package-private to the
    /// widget and deliberately not reached into: this test asserts the
    /// *behaviour* either side of the window, not the constant.
    private static final double AFTER_THE_WINDOW = 1001;

    private static final double WITHIN_THE_WINDOW = 200;

    private final TestHost host = new TestHost();

    /// Two names sharing a first letter and two sharing another, so both readings
    /// of a second keystroke — "narrow the prefix" and "start again" — land on a
    /// different row and are told apart by which one.
    private static final List<String> NAMES = List.of("Denmark", "Deseret", "Estonia", "Eritrea");

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private ElementTree tree() {
        return new ElementTree(ListView.of(NAMES).selection(Selection.MULTIPLE).selected(Set.of(), asked -> {}), host);
    }

    private static ListRow row(ElementTree tree, String id) {
        return Described.of(tree, ListRow.class).stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row \"" + id + "\""));
    }

    private static void type(ListRow row, String text) {
        row.onText(new TextEvent(text, null));
    }

    @Test
    @DisplayName("within the window, a second letter narrows the prefix")
    void aPrefixAccumulatesWhileTheWindowIsOpen() {
        var tree = tree();

        type(row(tree, "list-Denmark"), "d");
        assertEquals(
                List.of("list-Deseret"), host.focusRequests(), "\"d\" from Denmark is the next name starting with it");

        host.forgetFocusRequests();
        host.clock.advance(WITHIN_THE_WINDOW);
        // "de" still matches Denmark, which is where the focus already is, so the
        // list asks for no move at all. That silence IS the assertion: read as a
        // fresh "e" it would have jumped to Estonia.
        type(row(tree, "list-Deseret"), "e");

        assertEquals(
                List.of(), host.focusRequests(), "\"de\" is still Denmark — a fresh \"e\" would have gone to Estonia");
    }

    @Test
    @DisplayName("past the window, the same letter starts a new search")
    void theWindowExpires() {
        var tree = tree();

        type(row(tree, "list-Denmark"), "d");
        host.forgetFocusRequests();

        // The whole point of this file. A second of silence, asserted rather than
        // slept through.
        host.clock.advance(AFTER_THE_WINDOW);
        type(row(tree, "list-Deseret"), "e");

        assertEquals(
                List.of("list-Estonia"), host.focusRequests(), "after the window \"e\" is a new search, not \"de\"");
    }

    @Test
    @DisplayName("the window is measured from the last keystroke, not the first")
    void theWindowSlides() {
        var tree = tree();

        type(row(tree, "list-Denmark"), "d");
        host.forgetFocusRequests();

        // Two gaps that are each inside the window but together exceed it. A
        // timeout measured from the *first* letter would have expired by now; one
        // measured from the last has not, so "de" still holds.
        host.clock.advance(600);
        type(row(tree, "list-Deseret"), "e");
        host.clock.advance(600);
        type(row(tree, "list-Deseret"), "s");

        assertEquals(
                List.of(),
                host.focusRequests(),
                "\"des\" is Deseret, where the focus already is —"
                        + " so the search never restarted across two 600ms gaps");
    }
}
