package io.github.digitalsmile.goldberry.widgets.controls.chip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.registry.ActionRegistry;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widgets.Widgets;

/// §3's `chip` — the small rounded label you can choose and take away
/// ([ADR-0305]).
///
/// Nothing here needs the native library: what a chip *looks* like is
/// [ChipGoldenTest]'s, and what is asserted below is the value, the states it
/// reports and what it does when it is pressed.
class ChipTest {

    private final List<String> log = new ArrayList<>();

    /// An event needs an element to name as its target, and the cheapest true
    /// one is the widget's own — a one-node tree, which is what every other
    /// control's test builds for the same reason.
    private static Element elementOf(Widget widget) {
        return new ElementTree(widget).root();
    }

    private static PointerEvent click(Widget target) {
        return new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, elementOf(target));
    }

    private static KeyEvent press(Widget target, Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, elementOf(target));
    }

    @Nested
    @DisplayName("the value")
    class Value {

        @Test
        @DisplayName("the Java-built and KDL-built chips are equal values")
        void javaAndKdlAgree() {
            var fromKdl = Widgets.inflater().inflateAll(KdlParser.parse("""
                    chip id="unread" class="info" "Unread"
                    """)).getFirst();

            assertEquals(new Chip("Unread").id("unread").styled("info"), fromKdl);
        }

        @Test
        @DisplayName("a chip with no word in it is refused")
        void anEmptyLabelIsRefused() {
            // §13: a chip is a word you can choose. One with no word is a
            // coloured dot that nothing can announce.
            var thrown = assertThrows(IllegalArgumentException.class, () -> new Chip(""));

            assertTrue(thrown.getMessage().contains("label"), thrown.getMessage());
        }

        @Test
        @DisplayName("a dot and an icon at once is refused")
        void theLeadingSlotHoldsOneThing() {
            var icon = Icon.of("dot", "M12 12h1", 16);

            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> new Chip("Live").withDot(true).withIcon(icon));

            assertTrue(thrown.getMessage().contains("not both"), thrown.getMessage());
            // And in the other order, so it is a property of the value rather
            // than of the order the withers were called in.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Chip("Live").withIcon(icon).withDot(true));
        }

        @Test
        @DisplayName("the withers commute and keep the attributes")
        void theWithersDoNotDisturbEachOther() {
            var chip = new Chip("Unread")
                    .selected(true)
                    .id("unread")
                    .styled("info")
                    .disabled(true);

            assertTrue(chip.selected());
            assertTrue(chip.disabled());
            assertEquals("unread", chip.id());
            assertEquals(Set.of("info"), chip.classes());
        }
    }

    @Nested
    @DisplayName("what it reports about itself")
    class States {

        @Test
        @DisplayName("selected is mirrored to :checked, like every other one-of-a-set")
        void selectedIsChecked() {
            assertFalse(new Chip("Unread").isChecked());
            assertTrue(new Chip("Unread").selected(true).isChecked());
        }

        @Test
        @DisplayName("a chip with nothing to do is not in the Tab order")
        void aReadOnlyChipIsNotFocusable() {
            // It is a `badge` with a different type name, and putting one in the
            // Tab order would strand a keyboard user on a word.
            assertFalse(new Chip("Draft").isFocusable());
            assertTrue(new Chip("Unread", false, () -> {}).isFocusable());
            assertTrue(new Chip("tag").onDismiss(() -> {}).isFocusable());
        }

        @Test
        @DisplayName("a disabled chip is out of the Tab order whatever it can do")
        void disabledLeavesTheTabOrder() {
            assertFalse(new Chip("Unread", false, () -> {}).disabled(true).isFocusable());
            assertTrue(new Chip("Unread", false, () -> {}).disabled(true).isDisabled());
        }

        @Test
        @DisplayName("it says it is one of a set, not a button")
        void theRoleIsOption() {
            assertEquals(Role.OPTION, new Chip("Unread").role());
            assertEquals("Unread", new Chip("Unread").accessibleName());
        }
    }

    @Nested
    @DisplayName("what it does")
    class Behaviour {

        @Test
        @DisplayName("a click presses it, and the click is consumed")
        void aClickPresses() {
            var chip = new Chip("Unread", false, () -> log.add("pressed"));

            var event = click(chip);
            chip.onPointer(event);

            assertEquals(List.of("pressed"), log);
            assertTrue(event.isConsumed());
        }

        @Test
        @DisplayName("it selects nothing itself — the application does")
        void itDoesNotSelectItself() {
            // ADR-0063: a chip whose handler does nothing stays as it was, which
            // is the visible form of "the model did not change".
            var chip = new Chip("Unread", false, () -> {});

            chip.onPointer(click(chip));

            assertFalse(chip.selected());
        }

        @Test
        @DisplayName("Space and Enter press it")
        void theKeyboardPresses() {
            var chip = new Chip("Unread", false, () -> log.add("pressed"));

            chip.onKey(press(chip, Key.SPACE));
            chip.onKey(press(chip, Key.ENTER));

            assertEquals(List.of("pressed", "pressed"), log);
        }

        @Test
        @DisplayName("Delete and Backspace dismiss it")
        void theKeyboardDismisses() {
            // The × is a 12-point pointer target and not a Tab stop, so this is
            // the only way a keyboard reaches it.
            var chip = new Chip("typescript").onDismiss(() -> log.add("gone"));

            chip.onKey(press(chip, Key.DELETE));
            chip.onKey(press(chip, Key.BACKSPACE));

            assertEquals(List.of("gone", "gone"), log);
        }

        @Test
        @DisplayName("a chip with no dismiss ignores Delete rather than swallowing it")
        void deleteFallsThroughWithoutADismiss() {
            var chip = new Chip("Unread", false, () -> log.add("pressed"));

            var event = press(chip, Key.DELETE);
            chip.onKey(event);

            assertEquals(List.of(), log);
            assertFalse(event.isConsumed(), "a chip that cannot be dismissed must not eat the key");
        }

        @Test
        @DisplayName("a disabled chip neither presses nor dismisses")
        void disabledDoesNothing() {
            var chip = new Chip("Unread", false, () -> log.add("pressed"))
                    .onDismiss(() -> log.add("gone"))
                    .disabled(true);

            chip.onPointer(click(chip));
            chip.onKey(press(chip, Key.ENTER));
            chip.onKey(press(chip, Key.DELETE));

            assertEquals(List.of(), log);
        }
    }

    @Nested
    @DisplayName("the parts")
    class Parts {

        @Test
        @DisplayName("a plain chip is one label and nothing else")
        void aPlainChipIsJustItsLabel() {
            var parts = new Chip("Draft").children();

            assertEquals(1, parts.size());
            assertInstanceOf(ChipLabel.class, parts.getFirst());
        }

        @Test
        @DisplayName("the dot comes before the label and the × after it")
        void theOrderIsDotLabelDismiss() {
            var parts = new Chip("Live").withDot(true).onDismiss(() -> {}).children();

            assertInstanceOf(ChipDot.class, parts.get(0));
            assertInstanceOf(ChipLabel.class, parts.get(1));
            assertInstanceOf(ChipDismiss.class, parts.get(2));
        }

        @Test
        @DisplayName("a disabled chip's × is wired to nothing at all")
        void aDisabledDismissCarriesNoHandler() {
            // Null rather than a no-op wrapper, so it cannot fire by a route
            // that forgot to check.
            var parts = new Chip("tag")
                    .onDismiss(() -> log.add("gone"))
                    .disabled(true)
                    .children();
            var dismiss = (ChipDismiss) parts.getLast();

            var event = click(dismiss);
            dismiss.onPointer(event);

            assertEquals(List.of(), log);
            assertTrue(event.isConsumed(), "the event must not fall through to the chip under it");
        }

        @Test
        @DisplayName("the × consumes its click, so dismissing is not also choosing")
        void theDismissConsumesItsClick() {
            var parts = new Chip("tag").onDismiss(() -> log.add("gone")).children();
            var dismiss = (ChipDismiss) parts.getLast();

            var event = click(dismiss);
            dismiss.onPointer(event);

            assertEquals(List.of("gone"), log);
            assertTrue(event.isConsumed());
        }

        @Test
        @DisplayName("no part is a Tab stop")
        void thePartsAreNotFocusable() {
            for (var part : new Chip("Live").withDot(true).onDismiss(() -> {}).children()) {
                assertFalse(
                        part instanceof Handles handles && handles.isFocusable(),
                        () -> part.getClass().getSimpleName() + " is a second Tab stop inside one control");
            }
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("`press` and `dismiss` resolve against the action registry")
        void theActionsAreWired() {
            var actions = ActionRegistry.strict()
                    .bind("pick", () -> log.add("picked"))
                    .bind("drop", () -> log.add("dropped"));

            var chip = (Chip) Widgets.inflater(actions)
                    .inflateAll(KdlParser.parse("chip press=\"pick\" dismiss=\"drop\" \"Unread\""))
                    .getFirst();

            chip.onPointer(click(chip));
            chip.onKey(press(chip, Key.DELETE));

            assertEquals(List.of("picked", "dropped"), log);
        }

        @Test
        @DisplayName("`dot`, `selected` and `disabled` are flags a document writes")
        void theFlagsInflate() {
            var chip = (Chip) Widgets.inflater()
                    .inflateAll(KdlParser.parse("chip dot=#true selected=#true disabled=#true \"Degraded\""))
                    .getFirst();

            assertTrue(chip.dot());
            assertTrue(chip.selected());
            assertTrue(chip.disabled());
        }

        @Test
        @DisplayName("the catalog registers `chip` and not its parts")
        void theRegistryListsTheWidgetAlone() {
            var registered = Widgets.inflater().registered();

            assertTrue(registered.contains("chip"));
            // ADR-0065: a part is CSS-selectable and deliberately not
            // KDL-constructible.
            assertFalse(registered.contains("chip-dot"));
            assertFalse(registered.contains("chip-label"));
            assertFalse(registered.contains("chip-dismiss"));
        }

        @Test
        @DisplayName("a chip with no label in the document is refused where it is written")
        void anEmptyDocumentChipIsRefused() {
            assertThrows(
                    Exception.class,
                    () -> Widgets.inflater().inflateAll(KdlParser.parse("chip")).getFirst());
        }
    }

    @Test
    @DisplayName("an unattributed chip still chains from NONE")
    void chainingStartsFromNone() {
        assertEquals(Attributes.NONE, new Chip("Draft").attributes());
        assertEquals("draft", new Chip("Draft").id("draft").id());
    }
}
