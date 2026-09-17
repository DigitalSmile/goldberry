package io.github.digitalsmile.goldberry.widgets.panel.timeline;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// The slot an [Entry] takes a widget marker from — §10's "dot, icon or
/// `badge`", third case.
///
/// ```kdl
/// entry "Released" {
///     marker { badge class="success" "v2" }
///     text "Tagged and published."
/// }
/// ```
///
/// A description rather than a widget that draws itself —
/// [io.github.digitalsmile.goldberry.widgets.nav.wizard.WizardPage]'s arrangement:
/// the entry lifts the one widget out of it and puts it on the axis, and
/// everything else it was written beside stays the entry's body. A named child
/// rather than "the first `badge` in the body", because a badge is also
/// ordinary content and a document that wrote one under an event's words did
/// not ask for it to leave them (ADR-0356).
///
/// @param content    the one widget drawn on the axis
/// @param attributes `id` and `class`; the marker drawn is `content`, so these
///                   land nowhere and are kept only so the document round-trips
@Markup("marker")
public record EntryMarker(Widget content, Attributes attributes) implements Widget.Leaf, Attributed<EntryMarker> {

    public EntryMarker {
        Objects.requireNonNull(content, "content");
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A slot holding `content`.
    public EntryMarker(Widget content) {
        this(content, Attributes.NONE);
    }

    @Override
    public EntryMarker withAttributes(Attributes value) {
        return new EntryMarker(content, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Builds a `marker` from markup — exactly one child, because an axis has
    /// one point per event.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (children.size() != 1) {
            throw new IllegalArgumentException(
                    "a marker holds exactly one widget, the thing drawn on the axis; found " + children.size());
        }
        return new EntryMarker(children.getFirst(), Attributes.of(node));
    }
}
