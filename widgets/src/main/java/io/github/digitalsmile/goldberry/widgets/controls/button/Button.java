package io.github.digitalsmile.goldberry.widgets.controls.button;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// A button (§11, `docs/core-widgets.md` §3).
///
/// The first control, and the shape every other one copies: a Java record, a KDL
/// node, and a CSS type — the parity invariant — plus [Handles] for the two ways
/// a button is activated.
///
/// ## What it decides and what the stylesheet decides
///
/// Nothing visual is here. The height, the padding, the colours and the four
/// variants are all in `controls.css` in the toolkit-base layer, which is what
/// makes `button.primary` a stylesheet's business rather than a constructor
/// parameter. What the widget owns is behaviour: it is focusable, it activates on
/// a click and on `Space`/`Enter`, and it never fires twice for one gesture.
///
/// ## Why the action is a field on an immutable value
///
/// A widget is rebuilt constantly and holds no state ([ADR-0004]) — but a
/// `Runnable` is not state, it is part of the description. Rebuilding with a
/// different action simply means the button now does something else, which is
/// what an author changing it intends. Nothing here remembers a press: the press
/// is the router's, on the element ([ADR-0052], [ADR-0054]).
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
        String label, Icon icon, Runnable onPress, boolean disabled,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Button> {

    public Button {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty() && icon == null) {
            throw new IllegalArgumentException(
                    "a button with neither a label nor an icon has nothing to click on"
                            + " and nothing to read out (§13)");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A button with a label and an action.
    public Button(String label, Runnable onPress) {
        this(label, null, onPress, false, Attributes.NONE);
    }

    /// A button that does nothing yet.
    public Button(String label) {
        this(label, null, null, false, Attributes.NONE);
    }

    /// This button with an icon before its label (§11: "label, icon, or both").
    ///
    /// The icon is **borrowed**. A widget is a value that is rebuilt every frame
    /// and thrown away, so it must not own something with a `close()`; the
    /// application builds the icon once and keeps it, exactly as it keeps a
    /// `Font` ([ADR-0043](../../../../../../../../book/src/adr/0043-icons-are-stroked-paths.md)).
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
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// Focusable, which is what puts it in the Tab order and lets `:focus-visible`
    /// mean something (§7.2) — unless it is disabled, in which case Tab skips it.
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

    /// `Space` and `Enter` activate, per `docs/core-widgets.md` §3.
    ///
    /// Repeats are ignored: holding Space down is one activation, because a
    /// button is not a key. (A repeat-while-held control — a spinner's arrows —
    /// wants the opposite, and will say so.)
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat() || !event.modifiers().none()) {
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
        // an icon (§11: "content = label, icon, or both"). The gap between them
        // is the stylesheet's -- 6 points, per the design system.
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
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Button(Wiring.label(node), wiring.icon(node), wiring.action(node, "press"),
                Wiring.disabled(node), Attributes.of(node));
    }
}
