package io.github.digitalsmile.goldberry.widgets.markup;

import java.util.HashSet;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;

/// A widget a document places and the application describes — what `list`,
/// `table` and `tree` inflate to (ADR-0367).
///
/// ```kdl
/// list bind="app.people" id="people" class="sidebar"
/// ```
///
/// ## Why a document names the widget rather than describing it
///
/// A list's rows come from an item-factory, a table's cells from a
/// cell-factory, and a tree's children from suppliers. Each is a function, and
/// markup is data (§9): a document that could write one would be code in
/// another syntax. What a document *can* name is a value that changes, which is
/// what `bind=` is for, so the model holds the `ListView` it builds and the
/// document says where it goes and what it is called.
///
/// The element subscribes to the binding as it does for every bound widget, so a
/// model that replaces the value rebuilds this node, and the list under it
/// reconciles by key and keeps its state.
///
/// ## What the document adds
///
/// Its `id` wins when it gives one, and its classes are added to the widget's
/// own. A value that is not a `type` — nothing bound yet, or a binding to the
/// wrong thing — draws nothing rather than failing, which is the lenient
/// registry's rule for a document mid-edit.
///
/// @param source     the value `bind=` names, or null
/// @param type       what that value has to be to be drawn
/// @param attributes the document's `id` and `class`
public record Bound(@Nullable Observable<?> source, Class<? extends Widget> type, Attributes attributes)
        implements Widget.Stateless, Attributed<Bound>, Bindable<Bound> {

    public Bound {
        Objects.requireNonNull(type, "type");
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public Widget build(BuildContext context) {
        var value = source == null ? null : source.get();
        if (!type.isInstance(value) || !(value instanceof Attributed<?> attributed)) {
            return Widget.nothing();
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
}
