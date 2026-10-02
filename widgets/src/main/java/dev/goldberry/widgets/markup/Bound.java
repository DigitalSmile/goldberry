package dev.goldberry.widgets.markup;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.attr.Bindable;

/// A widget a document places and the application builds — what `list`,
/// `table`, `tree` and `slot` inflate to.
///
/// ```kdl
/// list bind="app.people" id="people" class="sidebar"
/// slot bind="app.detail" id="detail"
/// ```
///
/// A list's rows come from an item factory, a table's cells from a cell
/// factory, and a tree's children from suppliers. Each is a function, and a
/// document is data: it cannot write one. What a document can name is a value
/// that changes, so the model holds the `ListView` it builds, and the document
/// says where it goes and what it is called.
///
/// The element subscribes to the binding as it does for every bound widget, so a
/// model that replaces the value rebuilds this node, and the list under it
/// reconciles by key and keeps its state. The document's `id` wins when it
/// gives one, and its classes are added to the widget's own. A value that is
/// not a `type` — nothing bound yet, or a binding to the wrong thing — draws
/// nothing rather than failing, which is what a document mid-edit needs.
///
/// ## `slot` is the same for any widget
///
/// `slot bind="…"` is this over [Widget] itself: a region of a document whose
/// content the model decides, a detail pane that is a form for one selection
/// and a message for none. The model holds the widget it builds and replaces
/// it when the region should change, and the document says where it goes. A
/// widget with no [Attributed] to lay the document's `id` and classes over is
/// drawn as it is.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#what-markup-cannot-say).
///
/// @param source     the value `bind=` names, or null
/// @param type       what that value has to be to be drawn
/// @param attributes the document's `id` and `class`
@Markup("slot")
public record Bound(@Nullable Observable<?> source, Class<? extends Widget> type, Attributes attributes)
        implements Widget.Stateless, Attributed<Bound>, Bindable<Bound> {

    /// Written out so that the parameters that take null for a default can say so.
    public Bound(@Nullable Observable<?> source, Class<? extends Widget> type, @Nullable Attributes attributes) {
        Objects.requireNonNull(type, "type");
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.source = source;
        this.type = type;
        this.attributes = attributes;
    }

    @Override
    public Widget build(BuildContext context) {
        var value = source == null ? null : source.get();
        if (!type.isInstance(value) || !(value instanceof Widget widget)) {
            return Widget.nothing();
        }
        if (!(widget instanceof Attributed<?> attributed)) {
            return widget;
        }
        return attributed.withAttributes(merged(attributed.attributes()));
    }

    /// The widget's own attributes with the document's id and classes laid over.
    private Attributes merged(Attributes own) {
        var classes = new HashSet<>(own.classes());
        classes.addAll(attributes.classes());
        var result = own.classes(classes.toArray(String[]::new));
        return attributes.id() == null ? result : result.id(attributes.id());
    }

    @Override
    public Bound bound(Observable<?> value) {
        return new Bound(value, type, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public Bound withAttributes(Attributes value) {
        return new Bound(source, type, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Builds a `slot` from markup: whatever widget the `bind=` value holds.
    ///
    /// Children are ignored, for `list`'s reason: what is drawn is the model's.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Bound(wiring.bound(node), Widget.class, Attributes.of(node));
    }
}
