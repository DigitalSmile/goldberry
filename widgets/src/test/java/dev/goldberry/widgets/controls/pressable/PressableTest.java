package dev.goldberry.widgets.controls.pressable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.registry.ActionRegistry;
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.input.PointerRouter;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.hit.HitTest;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.text.Text;

/// `pressable` — a button's behaviour around anything, and none of its box.
///
/// The router is driven directly, over hand-made regions, because what is
/// under test is how a press, a key and a modal reach this node from inside
/// it, and not a layout or a paint.
///
/// Read more: [Buttons, badges and chips](https://goldberry.dev/docs/components/buttons.html#pressable).
class PressableTest {

    /// A box with an id and children and no behaviour: a picture, a row, the
    /// stand-in for whatever an application wraps.
    private record Plain(String name, List<Widget> kids, Attributes attributes)
            implements Widget.Leaf, Attributed<Plain>, Styled {

        Plain(String name, Widget... kids) {
            this(name, List.of(kids), Attributes.NONE.id(name));
        }

        @Override
        public List<Widget> children() {
            return kids;
        }

        @Override
        public Plain withAttributes(Attributes value) {
            return new Plain(name, kids, value);
        }

        @Override
        public String cssType() {
            return "plain";
        }

        @Override
        public @Nullable String id() {
            return attributes.id();
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public @Nullable Object key() {
            return attributes.key();
        }
    }

    /// A layer that holds the keyboard and the pointer while it is up, which is
    /// all a dialog is to the router.
    private record Modal(String name) implements Widget.Leaf, Styled, Handles {

        @Override
        public boolean isModal() {
            return true;
        }

        @Override
        public String cssType() {
            return "modal";
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }
    }

    private final List<String> log = new ArrayList<>();
    private ElementTree tree;
    private PointerRouter router;

    private Pressable photo() {
        return new Pressable("Open photo.jpg", () -> log.add("open"), new Plain("picture"))
                .withAttributes(Attributes.NONE.id("photo"));
    }

    /// The window is 100x100: the pressable is the left half and the picture
    /// inside it fills it, and `other` is the right half.
    private void mount(Widget pressable, Widget... more) {
        var children = new ArrayList<Widget>();
        children.add(pressable);
        children.add(new Plain("other"));
        children.addAll(List.of(more));
        tree = new ElementTree(new Plain("window", children, Attributes.NONE.id("window")));
        router = new PointerRouter();
        router.focusRoot(tree.root());
        var regions = new ArrayList<HitTest.Region>();
        regions.add(HitTest.Region.of(find("window"), 0, 0, 100, 100));
        regions.add(HitTest.Region.of(find("photo"), 0, 0, 50, 100));
        for (var inner : List.of("picture", "inner")) {
            var element = find(inner);
            if (element != null) {
                regions.add(HitTest.Region.of(element, 0, 0, 50, 100));
            }
        }
        regions.add(HitTest.Region.of(find("other"), 50, 0, 50, 100));
        var modal = find("modal");
        if (modal != null) {
            regions.add(HitTest.Region.of(modal, 60, 10, 30, 30));
        }
        router.updateRegions(regions);
    }

    private @Nullable Element find(String id) {
        return find(tree.root(), id);
    }

    private static @Nullable Element find(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void click(float x, float y) {
        router.pointerMoved(x, y);
        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
        router.pointerReleased(x, y, PointerEvent.Button.PRIMARY, 1);
    }

    private void key(Key key, boolean repeat) {
        router.keyPressed(key, Modifiers.NONE, repeat);
    }

    @Nested
    @DisplayName("the pointer")
    class ThePointer {

        @Test
        @DisplayName("a click on what is inside it presses it, once")
        void clickInside() {
            mount(photo());

            click(20, 50);

            assertEquals(List.of("open"), log);
        }

        @Test
        @DisplayName("a press dragged off it and let go is not a click")
        void cancelled() {
            mount(photo());

            router.pointerMoved(20, 50);
            router.pointerPressed(20, 50, PointerEvent.Button.PRIMARY, 1);
            router.pointerMoved(80, 50);
            router.pointerReleased(80, 50, PointerEvent.Button.PRIMARY, 1);

            assertEquals(List.of(), log);
        }

        @Test
        @DisplayName(":hover and :active light it from anywhere inside it")
        void statesFromInside() {
            mount(photo());
            var photo = find("photo");

            router.pointerMoved(20, 50);
            assertTrue(photo.hasState(PseudoClass.HOVER));

            router.pointerPressed(20, 50, PointerEvent.Button.PRIMARY, 1);
            assertTrue(photo.hasState(PseudoClass.ACTIVE));

            router.pointerReleased(20, 50, PointerEvent.Button.PRIMARY, 1);
            router.pointerMoved(80, 50);
            assertFalse(photo.hasState(PseudoClass.HOVER));
            assertFalse(photo.hasState(PseudoClass.ACTIVE));
        }

        @Test
        @DisplayName("a button inside it is pressed on its own, and the pressable hears nothing")
        void innerControlWins() {
            mount(new Pressable(
                            "Release 2.4",
                            () -> log.add("row"),
                            new Button("Deploy", () -> log.add("deploy")).id("inner"))
                    .withAttributes(Attributes.NONE.id("photo")));

            click(20, 50);

            assertEquals(List.of("deploy"), log);
        }

        @Test
        @DisplayName("behind a modal it is out of reach, like the rest of the application")
        void modal() {
            mount(photo(), new Modal("modal"));

            click(20, 50);

            assertEquals(List.of(), log);
            assertFalse(find("photo").hasState(PseudoClass.HOVER), "and it does not light up as if it could be");
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class TheKeyboard {

        @Test
        @DisplayName("it is a Tab stop, with the ring")
        void tabStop() {
            mount(photo());

            key(Key.TAB, false);

            assertSame(find("photo"), router.focused());
            assertTrue(find("photo").hasState(PseudoClass.FOCUS_VISIBLE));
        }

        @Test
        @DisplayName("Enter and Space press it, and a held key presses once")
        void enterAndSpace() {
            mount(photo());
            key(Key.TAB, false);

            key(Key.ENTER, false);
            key(Key.SPACE, false);
            key(Key.SPACE, true);
            key(Key.A, false);

            assertEquals(List.of("open", "open"), log);
        }

        @Test
        @DisplayName("a modified key is left for a shortcut")
        void modified() {
            var pressable = photo();

            pressable.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.of(Mod.CTRL), false, null));

            assertEquals(List.of(), log);
        }

        @Test
        @DisplayName("a key bubbling up from something focused inside it is not a press")
        void notFromInside() {
            mount(new Pressable(
                            "Release 2.4",
                            () -> log.add("row"),
                            new Button("Deploy", () -> log.add("deploy")).id("inner"))
                    .withAttributes(Attributes.NONE.id("photo")));
            var row = (Pressable) find("photo").widget();

            row.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, find("inner")));

            assertEquals(List.of(), log);
        }
    }

    @Nested
    @DisplayName("disabled")
    class Disabled {

        @Test
        @DisplayName("is no Tab stop, takes no click, and says so to the stylesheet")
        void disabled() {
            mount(photo().disabled(true));

            key(Key.TAB, false);
            click(20, 50);

            assertNull(router.focused());
            assertEquals(List.of(), log);
            assertTrue(photo().disabled(true).isDisabled());
            assertFalse(photo().disabled(true).isFocusable());
        }
    }

    @Nested
    @DisplayName("what a reader is told")
    class Semantics {

        @Test
        @DisplayName("a button, with the name it was given")
        void roleAndName() {
            var photo = photo();

            assertEquals(Role.BUTTON, photo.role());
            assertEquals("Open photo.jpg", photo.accessibleName());
        }

        @Test
        @DisplayName("name= wins over it, as on every widget")
        void attributeWins() {
            var photo = photo().withAttributes(Attributes.NONE.name("Open the photo"));

            assertEquals("Open the photo", photo.accessibleName());
        }

        @Test
        @DisplayName("a blank name is refused, because its content is not words a reader can say")
        void nameRequired() {
            assertThrows(IllegalArgumentException.class, () -> new Pressable(" ", () -> {}, new Plain("p")));
        }

        @Test
        @DisplayName("it styles nothing of its own: its type is pressable and its classes are the document's")
        void styled() {
            var photo = photo().styled("release-row");

            assertEquals("pressable", photo.cssType());
            assertEquals(Set.of("release-row"), photo.classes());
            assertEquals(1, photo.children().size());
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("name=, press= and disabled= inflate, and the children are the content")
        void inflates() {
            var actions = ActionRegistry.strict().bind("viewer.open", () -> log.add("open"));
            var pressable = assertInstanceOf(
                    Pressable.class,
                    Widgets.inflater(actions).inflate(KdlParser.parse("""
                            pressable name="Open photo.jpg" press="viewer.open" class="chat-image" {
                                text "photo.jpg"
                            }
                            """).getFirst()));

            assertEquals("Open photo.jpg", pressable.accessibleName());
            assertEquals(Set.of("chat-image"), pressable.classes());
            assertInstanceOf(Text.class, pressable.content().getFirst());
            assertFalse(pressable.disabled());
            pressable.onPress().run();
            assertEquals(List.of("open"), log);
        }

        @Test
        @DisplayName("a pressable with no name= is refused with the attribute named")
        void nameRequired() {
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> Widgets.inflater()
                            .inflate(KdlParser.parse("pressable press=\"viewer.open\" { text \"x\" }")
                                    .getFirst()));

            assertTrue(thrown.getMessage().contains("name="), thrown.getMessage());
        }
    }
}
