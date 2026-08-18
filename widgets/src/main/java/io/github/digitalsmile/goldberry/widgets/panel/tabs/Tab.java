package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// One tab of a [Tabs] — its label, its icon, its colour, and the content behind
/// it.
///
/// ```kdl
/// tab value="editor" icon="file" colour="#bf616a" closable=#true "Editor" {
///     text "whatever the tab shows"
/// }
/// ```
///
/// ## The header is the widget; the content is carried
///
/// What this node *draws* is the header: an optional icon, the label, and a close
/// affordance when it is closable. Its [#content] is the other half — the widgets
/// the panel shows when this tab is the selected one — and it is **not** drawn by
/// this node. [Tabs] takes it out and puts it in the panel, which is what makes
/// the content lazy: only the selected tab's content is ever built into an
/// element, so nine unselected tabs cost nine headers and nothing else.
///
/// ## Colour
///
/// `colour` is the one place a widget in this catalog takes a colour as a value
/// rather than from a stylesheet, and the reason is that **a stylesheet cannot
/// know it**: a tab coloured after the project it belongs to is application data,
/// like a label, and there is no selector for "the tab whose project is red". It
/// is written through `restyle`, so a stylesheet still decides *what* the colour
/// means — `controls.css` puts it on the selected tab's underline and on its icon
/// — and an application that sets none gets the theme's accent
/// ([ADR-0107](../../../../../../../../book/src/adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)).
///
/// @param value      what this tab is called in the model — what `change` reports
/// @param label      the text in the header
/// @param icon       an optional icon before it
/// @param colour     `0xAARRGGBB`, or 0 for the theme's accent
/// @param closable   whether the header carries a close affordance
/// @param content    what the panel shows when this tab is selected
/// @param selected   supplied by [Tabs] on every build; not an attribute, for
///                   `radio`'s reason — a document that could mark two tabs
///                   selected would break the invariant the strip exists to hold
/// @param onSelect   supplied by [Tabs]
/// @param onClose    supplied by [Tabs]
/// @param attributes `id` and `class`, exactly as on the primitives
public record Tab(
        String value, String label, Icon icon, int colour, boolean closable,
        List<Widget> content, boolean selected, Runnable onSelect, Runnable onClose,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Tab> {

    public Tab {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(label, "label");
        content = List.copyOf(content == null ? List.of() : content);
        if (label.isEmpty() && icon == null) {
            throw new IllegalArgumentException(
                    "a tab with neither a label nor an icon has nothing to click on"
                            + " and nothing to read out (§13)");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A tab with a value and a label, and whatever it shows.
    public Tab(String value, String label, Widget... content) {
        this(value, label, null, 0, false, List.of(content), false, null, null, Attributes.NONE);
    }

    /// This tab with an icon before its label.
    public Tab icon(Icon value) {
        return new Tab(this.value, label, value, colour, closable, content, selected, onSelect,
                onClose, attributes);
    }

    /// This tab in a colour of its own — see the class note.
    public Tab colour(int argb) {
        return new Tab(value, label, icon, argb, closable, content, selected, onSelect, onClose,
                attributes);
    }

    /// This tab with a close affordance in its header, which raises the strip's
    /// `close` rather than removing anything: what a tab strip shows is the
    /// application's list, and only the application may shorten it (ADR-0063).
    public Tab closable(boolean value) {
        return new Tab(this.value, label, icon, colour, value, content, selected, onSelect,
                onClose, attributes);
    }

    /// Used by [Tabs] to tell a tab what it is and what it may ask for.
    Tab wired(boolean isSelected, Runnable select, Runnable close) {
        return new Tab(value, label, icon, colour, closable, content, isSelected, select, close,
                attributes);
    }

    @Override
    public String cssType() {
        return "tab";
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
    public Tab withAttributes(Attributes value) {
        return new Tab(this.value, label, icon, colour, closable, content, selected, onSelect,
                onClose, value);
    }

    /// A tab takes the focus — it is what the strip's arrows rove between.
    @Override
    public boolean isFocusable() {
        return true;
    }

    /// Mirrored to `:checked`, which is how `controls.css` draws the selected tab
    /// — the same pseudo-class a `radio` and an `option` use, because it is the
    /// same fact about a set.
    @Override
    public boolean isChecked() {
        return selected;
    }

    /// The underline, and the close affordance when there is one.
    ///
    /// The close is not a `button`: it is a part, so it is styleable and not
    /// constructible, and it must not be a second Tab stop — the strip is one.
    /// The indicator is always built, selected or not, so that it can transition
    /// (see [TabIndicator]).
    @Override
    public List<Widget> children() {
        return closable
                ? List.of(new TabIndicator(selected, colour), new TabClose(onClose))
                : List.of(new TabIndicator(selected, colour));
    }

    /// The tab's own colour, written where a transition can see it — and only
    /// when one was given, so a tab with no colour is styled entirely by the
    /// stylesheet rather than by a value that happens to equal the default.
    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        return colour == 0 ? resolved : resolved.color(colour);
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED && onSelect != null) {
            onSelect.run();
            event.consume();
        }
    }

    /// `Space` and `Enter` select. The arrows are the strip's, because a tab strip
    /// is one Tab stop with a roving selection (§7.2).
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            if (onSelect != null) {
                onSelect.run();
            }
            event.consume();
        } else if (event.key() == Key.DELETE && closable && onClose != null) {
            // `Delete` on a closable tab, which is the keyboard's answer to a
            // close affordance nobody can click without a pointer.
            onClose.run();
            event.consume();
        }
    }

    /// The indicator **first**, so it is painted under the label, then the icon,
    /// the label, and the close affordance last.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new ArrayList<Box>(4);
        // Child 0 is the indicator, which is out of flow and takes no space.
        content.add(children.getFirst());
        if (icon != null) {
            content.add(Box.icon(icon, style.color()));
        }
        if (!label.isEmpty()) {
            content.add(Box.text(context.paragraph(style, label), style.color()));
        }
        // Anything after the indicator is the close affordance.
        content.addAll(children.subList(1, children.size()));
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }
}
