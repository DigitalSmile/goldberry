package dev.goldberry.widgets.nav.breadcrumbs;

import java.util.ArrayList;
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

/// One step of a [Breadcrumbs] — a label, an optional icon, and where it goes.
///
/// ```kdl
/// crumb icon="home" press="app.go-home" "Home"
/// crumb "The Red Book"
/// ```
///
/// ## `current` is the trail's word, not the document's
///
/// [dev.goldberry.widgets.panel.tabs.Tab]'s arrangement
/// exactly: the strip supplies it on every build, and the node it is written on
/// cannot be told otherwise. A trail's one invariant is
/// that the **last** crumb is where you are, and a document that could mark a
/// middle crumb current — or none of them — would be able to describe a path that
/// does not end anywhere.
///
/// A current crumb is not focusable and does not run its handler. Both follow
/// from the rule that the last crumb is the current page and is **not** a link, and both are
/// enforced here rather than by the trail declining to pass the handler down, so
/// that a `Crumb` built by hand in a test behaves the way one built by a trail
/// does.
///
/// ## Mirrored to `:checked`
///
/// The same pseudo-class a `tab`, a `radio` and an `option` use, because it is the
/// same fact: one of a set is the one. `crumb:checked` is the whole of styling
/// where-you-are, and `controls.css` spends it on weight and ink rather than on a
/// fill — a filled current crumb reads as a button, which is precisely what it
/// must not.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html#crumb).
///
/// @param label      the step's name
/// @param icon       an optional icon before it — in practice only the first
///                   crumb has one, because a row of icons reads as a toolbar
/// @param onPress    where this step goes, or null for a crumb that is only a
///                   word. Ignored when [#current]
/// @param current    supplied by [Breadcrumbs] on every build; not an attribute
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("crumb")
public record Crumb(
        String label, @Nullable Icon icon, @Nullable Runnable onPress, boolean current, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Crumb>, Semantics {

    /// Written out so that the parameters taking null for a default can say so.
    public Crumb(
            String label,
            @Nullable Icon icon,
            @Nullable Runnable onPress,
            boolean current,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty()) {
            throw new IllegalArgumentException(
                    "a crumb needs a label: a trail is read as a sentence, and a step with no word"
                            + " in it is a gap between two chevrons");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.label = label;
        this.icon = icon;
        this.onPress = onPress;
        this.current = current;
        this.attributes = attributes;
    }

    /// A step with a name and somewhere to go.
    public Crumb(String label, Runnable onPress) {
        this(label, null, onPress, false, Attributes.NONE);
    }

    /// A step that is only a word — the last one, usually, though the trail marks
    /// that itself.
    public Crumb(String label) {
        this(label, null, null, false, Attributes.NONE);
    }

    /// This step with an icon before its label. The icon is **borrowed**: the
    /// caller owns it and closes it, and the crumb only draws it.
    public Crumb withIcon(Icon value) {
        return new Crumb(label, Objects.requireNonNull(value, "icon"), onPress, current, attributes);
    }

    /// Used by [Breadcrumbs] to tell a crumb that it is where you are.
    Crumb asCurrent(boolean value) {
        return new Crumb(label, icon, onPress, value, attributes);
    }

    @Override
    public Crumb withAttributes(Attributes value) {
        return new Crumb(label, icon, onPress, current, value);
    }

    @Override
    public String cssType() {
        return "crumb";
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
    public boolean isChecked() {
        return current;
    }

    /// Focusable when it leads somewhere and is not where you already are.
    ///
    /// The second half is the current-page rule and the first is the one that keeps a trail
    /// from being a row of Tab stops that do nothing: a crumb with no handler is
    /// a word, and a word does not take the keyboard.
    @Override
    public boolean isFocusable() {
        return !current && onPress != null;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            follow();
            // Consumed whether or not anything ran, so a click on the current
            // page does not fall through to whatever is behind the trail.
            event.consume();
        }
    }

    /// `Space` and `Enter`, which is the rule for everything you press.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            follow();
            event.consume();
        }
    }

    private void follow() {
        if (!current && onPress != null) {
            onPress.run();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new ArrayList<Box>(2);
        if (icon != null) {
            // Whole or not at all: a crumb that gives way spends its missing
            // pixels on the label, which has an ellipsis to say so.
            content.add(Box.icon(icon, style.color()).shrink(0));
        }
        // The crumb's own flow, passed down by hand because the label is an
        // anonymous box no style is applied to: the stylesheet writes `nowrap`
        // and `ellipsis` on `crumb`, and the current crumb is the one that may be
        // narrower than its label -- a too-long title is cut with a `…` instead
        // of painting past the trail.
        content.add(Box.text(context.paragraph(style, label), style.color(), style.textFlow()));
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// [Role#BUTTON], where a link would be the better word.
    ///
    /// There is no `LINK` in [Role] and this is not the place to add one: a role
    /// nothing can consume is a value written down for a bridge that does not
    /// exist yet, and `BUTTON` — "something you press to make it happen" — is
    /// true of a crumb in every way that matters to somebody listening. The
    /// navigation landmark a trail would also carry has nowhere to go until
    /// there is an accessibility bridge, and is not approximated here.
    @Override
    public Role role() {
        return Role.BUTTON;
    }

    @Override
    public String accessibleName() {
        return label;
    }

    /// Builds a `crumb` from markup.
    ///
    /// No `current`, for the reason in the class note — the trail writes it.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Crumb(
                Wiring.label(node), wiring.icon(node), wiring.action(node, "press"), false, Attributes.of(node));
    }
}
