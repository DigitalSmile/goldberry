package dev.goldberry.widgets.controls.pressable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// Anything, made into something you press: a button's behaviour with none of
/// a button's box.
///
/// ```kdl
/// pressable name="Open photo.jpg" press="viewer.open" {
///     image src="photo.jpg" alt=""
/// }
/// ```
///
/// ```java
/// new Pressable("Open photo.jpg", () -> viewer.open(file), new ImageView(source, ""))
/// ```
///
/// ## The behaviour is a button's
///
/// It is a Tab stop. A click activates it, and so do `Space` and `Enter`
/// while it has the keyboard, once per press however long the key is held.
/// `:hover`, `:active` and `:focus-visible` match it the way they match a
/// `button`, from anywhere inside it, because the router marks the whole
/// chain under the pointer. `disabled` takes it out of the Tab order, refuses
/// activation and matches `:disabled`, for everything inside it too. Behind a
/// modal dialog it is out of reach like the rest of the application.
///
/// A press that something inside it handles is that thing's: a `button` in a
/// pressable row is pressed on its own and the row hears nothing. A key that
/// reaches it from something focused inside it is left alone, so `Enter` in a
/// field does not also press the row the field is in.
///
/// ## The look is the application's
///
/// The stylesheet gives it a focus ring and its `disabled` fade, and nothing
/// else: no padding, no surface, no hover wash. It lays its children out in a
/// column like a `panel`, and a row of content is a `row` inside it. That is
/// the point of it: a picture that opens a viewer, a release row, a card. Each
/// of those already has a look, and a `button` around it would draw a second.
///
/// ## It has to be named
///
/// A reader announces it as a button, and a button with no name is a control
/// nobody can find. Its content is arbitrary, a picture or a row of three
/// texts, so the name cannot be worked out from it the way a `button`'s label
/// is. So it is required, and refused when it is blank. `name=` in
/// [Attributes], when set, wins over it, exactly as on every other widget.
///
/// Read more: [Buttons, badges and chips](https://goldberry.dev/docs/components/buttons.html#pressable).
///
/// @param name       what a reader announces it as; required
/// @param onPress    what activating it does, or null for one not yet wired
/// @param disabled   whether it refuses activation and matches `:disabled`
/// @param content    what is drawn: anything, laid out in a column
/// @param attributes `id`, `class` and the rest, exactly as on every widget
@Markup("pressable")
public record Pressable(
        String name, @Nullable Runnable onPress, boolean disabled, List<Widget> content, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Pressable>, Semantics {

    /// Written out so that the parameters taking null for a default can say so.
    public Pressable(
            String name,
            @Nullable Runnable onPress,
            boolean disabled,
            @Nullable List<Widget> content,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a pressable needs a name: a reader announces it as a button, and"
                    + " what is inside it is not words it can read out. `pressable name=\"Open photo.jpg\"`");
        }
        this.name = name;
        this.onPress = onPress;
        this.disabled = disabled;
        this.content = List.copyOf(content == null ? List.of() : content);
        this.attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// `content`, named and doing `onPress` when pressed.
    public Pressable(String name, Runnable onPress, Widget... content) {
        this(name, onPress, false, List.of(content), Attributes.NONE);
    }

    /// This pressable, disabled or not.
    ///
    /// Still laid out, painted and hit-tested, as a disabled button is, so a
    /// click on it lands nowhere rather than on whatever is behind it.
    public Pressable disabled(boolean value) {
        return new Pressable(name, onPress, value, content, attributes);
    }

    /// This pressable doing something else.
    public Pressable onPress(@Nullable Runnable value) {
        return new Pressable(name, value, disabled, content, attributes);
    }

    @Override
    public Pressable withAttributes(Attributes value) {
        return new Pressable(name, onPress, disabled, content, value);
    }

    @Override
    public String cssType() {
        return "pressable";
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

    @Override
    public List<Widget> children() {
        return content;
    }

    /// A Tab stop unless disabled, for a button's reason: a control the
    /// keyboard can land on and that does nothing strands whoever is using it.
    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// Activates on a click, not on a release, so a press dragged off it and
    /// let go is cancelled. Reached by bubbling from whatever was hit inside
    /// it, unless that took the click first.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            activate();
            event.consume();
        }
    }

    /// `Space` and `Enter`, unrepeated and unmodified, while this has the
    /// keyboard itself.
    ///
    /// A key bubbling up from something focused inside it belongs to that, and
    /// is left alone even when that declined it.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        var target = event.target();
        if (target != null && target.widget() != this) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            activate();
            event.consume();
        }
    }

    /// Runs the action, unless disabled — checked here as well as by the
    /// router, so no route to it can forget.
    private void activate() {
        if (!disabled && onPress != null) {
            onPress.run();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    @Override
    public Role role() {
        return Role.BUTTON;
    }

    /// `name=` when it was given, the required name otherwise.
    @Override
    public String accessibleName() {
        return Objects.requireNonNullElse(attributes.name(), name);
    }

    /// Builds a `pressable` from markup.
    ///
    /// `name=` is required, and is the accessible name as it is on every node.
    /// `press=` names the action, `disabled=` disables it, and the
    /// children are the content.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var name = node.stringProperty("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a pressable needs name=: a reader announces it as a button, and"
                    + " its content is not words it can read out. `pressable name=\"Open photo.jpg\""
                    + " press=\"viewer.open\" { … }`");
        }
        // `name=` is the component here, so it is not also kept as an attribute
        // saying the same thing a second time.
        var attributes = Attributes.of(node).name(null);
        return new Pressable(name, wiring.action(node, "press"), Wiring.disabled(node), children, attributes);
    }
}
