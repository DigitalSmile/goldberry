package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.Widgets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
/// @param label      the text on the button
/// @param onPress    what activating it does; may be null for a button that is
///                   there to be styled and not yet wired
/// @param attributes `id` and `class`, exactly as on the primitives
public record Button(String label, Runnable onPress, Widgets.Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles {

    public Button {
        Objects.requireNonNull(label, "label");
        attributes = attributes == null ? Widgets.Attributes.NONE : attributes;
    }

    /// A button with a label and an action.
    public Button(String label, Runnable onPress) {
        this(label, onPress, Widgets.Attributes.NONE);
    }

    /// A button that does nothing yet.
    public Button(String label) {
        this(label, null, Widgets.Attributes.NONE);
    }

    /// This button with a class added — `button.primary` from Java.
    ///
    /// Classes rather than a variant enum, because §11's parity invariant says
    /// the Java form and the KDL form must produce equal values, and KDL spells a
    /// variant `class="primary"`. An enum would be a second vocabulary that only
    /// one of the two could use.
    public Button styled(String... classes) {
        return new Button(label, onPress,
                new Widgets.Attributes(attributes.id(), Set.of(classes), attributes.key()));
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
    /// mean something (§7.2).
    @Override
    public boolean isFocusable() {
        return true;
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
        // The label is a child box rather than text on the button's own box: a
        // box with text is a measured leaf and Yoga never lays a measured node's
        // children out, so a button that held its own text could never also hold
        // an icon (§11: "content = label, icon, or both").
        var text = Box.text(Paragraph.of(context.font(), label), style.color());
        return Box.of().style(style).children(text);
    }

    private void activate() {
        if (onPress != null) {
            onPress.run();
        }
    }
}
