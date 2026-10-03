package dev.goldberry.widgets.controls.button;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
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

/// A label, an icon, or both, with one action behind it.
///
/// ```kdl
/// button press="app.save" "Save"
/// button class="primary" icon="plus" press="app.create" "New"
/// button class="ghost" disabled=#true "Later"
/// ```
///
/// In Java, `new Button("Save", actions::save)`, then [#withIcon], [#disabled]
/// and `styled("primary")` as needed. The widget owns the behaviour: it is
/// focusable unless disabled, it activates on a click and on `Space` or
/// `Enter`, and it never fires twice for one gesture. Everything visual — the
/// height, the padding, the colours and the variant classes `primary`,
/// `danger`, `ghost`, `link` and `outlined` — is the stylesheet's, which is what
/// makes `button.primary` a class rather than a constructor parameter.
///
/// The action is a field on an immutable value, because a widget is rebuilt
/// every frame and a `Runnable` is part of the description rather than state:
/// rebuilding with a different action means the button now does something
/// else. Nothing here remembers a press; the press is the router's, on the
/// element. A disabled button still lays out, paints and hit-tests, so a click
/// on it lands nowhere rather than on whatever is behind it.
///
/// Read more: [Buttons, badges and chips](https://goldberry.dev/docs/components/buttons.html#button).
///
/// @param label      the text on the button; empty for an icon-only button
/// @param icon       the icon before the label, or null. Built at the size it
///                   draws at, and **not** closed by the button — the
///                   application owns it, because a widget is a value that gets
///                   rebuilt and a value must not own a native resource
/// @param onPress    what activating it does; may be null for a button that is
///                   there to be styled and not yet wired
/// @param disabled   whether it refuses activation and matches `:disabled`
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("button")
public record Button(
        String label, @Nullable Icon icon, @Nullable Runnable onPress, boolean disabled, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Button>, Semantics {

    /// The canonical constructor, written out because `attributes` takes null for a default.
    public Button(
            String label,
            @Nullable Icon icon,
            @Nullable Runnable onPress,
            boolean disabled,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty() && icon == null) {
            throw new IllegalArgumentException(
                    "a button with neither a label nor an icon has nothing to click on" + " and nothing to read out");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.label = label;
        this.icon = icon;
        this.onPress = onPress;
        this.disabled = disabled;
        this.attributes = attributes;
    }

    /// A button with a label and an action.
    public Button(String label, Runnable onPress) {
        this(label, null, onPress, false, Attributes.NONE);
    }

    /// A button that does nothing yet.
    public Button(String label) {
        this(label, null, null, false, Attributes.NONE);
    }

    /// This button with an icon before its label.
    ///
    /// The icon is **borrowed**. A widget is a value that is rebuilt every frame
    /// and thrown away, so it must not own something with a `close()`; the
    /// application builds the icon once and keeps it, exactly as it keeps a
    /// `Font`.
    public Button withIcon(Icon icon) {
        return new Button(label, Objects.requireNonNull(icon, "icon"), onPress, disabled, attributes);
    }

    /// This button, disabled or not.
    ///
    /// A disabled button still lays out, still paints, and still hit-tests —
    /// what it does not do is activate. That is deliberate: a control that
    /// vanished from hit testing would let a click land on whatever is behind
    /// it, which is worse than a click that does nothing.
    public Button disabled(boolean value) {
        return new Button(label, icon, onPress, value, attributes);
    }

    @Override
    public Button withAttributes(Attributes attributes) {
        return new Button(label, icon, onPress, disabled, attributes);
    }

    @Override
    public String cssType() {
        return "button";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    /// The document's classes — plus `circle` for an icon-only button that was
    /// not told `square`.
    ///
    /// An icon-only button is a disc on every desktop, so a button given an icon
    /// and no label is a circle by default rather than needing `class="circle"`
    /// to get the obvious result; `class="square"` overrides it.
    @Override
    public Set<String> classes() {
        var written = attributes.classes();
        if (!label.isEmpty() || icon == null || written.contains(SQUARE) || written.contains(CIRCLE)) {
            return written;
        }
        var classes = new java.util.HashSet<>(written);
        classes.add(CIRCLE);
        return Set.copyOf(classes);
    }

    /// The shape classes, and the placement one. Named so a caller does not
    /// spell them twice.
    public static final String SQUARE = "square";

    public static final String CIRCLE = "circle";

    public static final String OUTLINED = "outlined";

    public static final String FLOAT = "float";

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Focusable, which is what puts it in the Tab order and lets `:focus-visible`
    /// mean something — unless it is disabled, in which case Tab skips it.
    ///
    /// A disabled control that could still be focused would strand a keyboard
    /// user on something that does not respond.
    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// Activates on a click — not on a release.
    ///
    /// The distinction is the whole reason [PointerEvent.Kind#CLICKED] exists: a
    /// press dragged off the button and let go is a cancelled click, and a button
    /// that fired on release would have no way to tell.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            activate();
            event.consume();
        }
    }

    /// `Space` and `Enter` activate.
    ///
    /// Repeats are ignored: holding Space down is one activation, because a
    /// button is not a key. (A repeat-while-held control — a spinner's arrows —
    /// wants the opposite, and will say so.)
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            activate();
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The content is child boxes rather than text on the button's own box: a
        // box with text is a measured leaf and Yoga never lays a measured node's
        // children out, so a button that held its own text could not also hold
        // an icon. The gap between them is the stylesheet's -- 6 points, per the
        // design system.
        var content = new java.util.ArrayList<Box>(2);
        if (icon != null) {
            content.add(Box.icon(icon, style.color()));
        }
        if (!label.isEmpty()) {
            content.add(Box.text(context.paragraph(style, label), style.color()));
        }
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// Runs the action, unless disabled.
    ///
    /// Checked here rather than at every call site, so a control cannot be
    /// activated by a route that forgot — a keyboard shortcut, a synthetic event
    /// from a test, an accessibility action later.
    private void activate() {
        if (!disabled && onPress != null) {
            onPress.run();
        }
    }

    /// Builds a `button` from markup.
    ///
    /// `press=` names an action and `icon=` names an icon, and neither can be
    /// *built* by a document: an `Icon` owns native memory and has to be closed,
    /// so one reloaded on every keystroke would leak per reload.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var button = button(node, wiring);
        // `float=#true` is placement, not appearance, so it composes with every
        // variant and shape above. Lifted out of layout by a stateful wrapper
        // that puts the button in the window's overlay layer.
        if (node.booleanProperty(FLOAT)) {
            return new Floated(button, Floated.corner(node.stringProperty("corner")));
        }
        return button;
    }

    /// The button itself, floated or not.
    private static Button button(KdlNode node, Wiring wiring) {
        var label = Wiring.label(node);
        var glyph = wiring.icon(node);
        // A document that names an icon and gets none has a *registry* problem,
        // and the constructor below cannot know that -- it sees a button with
        // neither, and says so, which sends the author to look at a KDL file
        // where the icon is plainly written. `Icons.lenient()` answering null is
        // the whole of it, and an icon-only button is the one shape where that
        // answer is fatal rather than cosmetic.
        if (label.isEmpty() && glyph == null && node.stringProperty("icon") != null) {
            throw new IllegalArgumentException("button icon=\"" + node.stringProperty("icon")
                    + "\" has no label to fall back on, and the icon registry did not supply that name."
                    + " Bind it — Icons.strict().bind(\"" + node.stringProperty("icon") + "\", …) — or give the button"
                    + " a label, because an icon-only button in a document is only as real as its registry");
        }
        return new Button(label, glyph, wiring.action(node, "press"), Wiring.disabled(node), Attributes.of(node));
    }

    @Override
    public Role role() {
        return Role.BUTTON;
    }

    /// The label, or the explicit `name=` when there is no label to read.
    ///
    /// **An icon-only button has an empty label by construction** — the icon is the
    /// whole of what is on screen — so deriving a name from it produces the empty
    /// string, which is a control a reader cannot announce. `name=` is for
    /// exactly this case, and it lives on `Attributes`, where every widget gets
    /// it rather than each remembering its own.
    ///
    /// The label wins where there is one. An author who writes both has said the
    /// same thing twice, and the one on screen is the one a sighted user is
    /// reading aloud to somebody else.
    @Override
    public @Nullable String accessibleName() {
        return label.isEmpty() ? attributes.name() : label;
    }
}
