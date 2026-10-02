package dev.goldberry.widgets.controls.select;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The text in a closed [Select] — a part, styleable as `select-value` and not
/// constructible from outside the package.
///
/// A node of its own rather than text on the field, for the reason a `button`'s
/// content is boxes: a box with text is a measured leaf and layout never lays a
/// measured node's children out, so a field that held its own text could not also
/// hold the chevron beside it.
///
/// It carries whether it is showing the **placeholder**, which is the one thing a
/// stylesheet needs to tell apart here: the placeholder is in the muted ink where
/// a chosen value is in the text ink, and the two are otherwise the same node in
/// the same place. Expressed as the class `placeholder` rather than a
/// pseudo-class because nothing in the stylesheet subset means "this is standing
/// in for a value" and inventing a pseudo-class for one widget would be
/// inventing a language.
///
/// ## As wide as the widest option
///
/// The field does not move when its value does: the cell's preferred width is
/// the widest of `widths` — every option's label and the placeholder, shaped
/// against this node's own resolved style — so choosing "Nord Dark" after "Light"
/// changes the word and not the control. Preferred, not minimum: the stylesheet
/// still shrinks it, so a select in a narrow column ellipsizes as it did. What is
/// measured is text, a function of the model and the style, rather than last
/// frame's geometry, so nothing here depends on how the previous frame came out.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#select).
///
/// @param text        the label to draw, which may be empty
/// @param placeholder whether `text` is the placeholder rather than a chosen
///                    option's label
/// @param widths      the labels whose widest decides the cell's width; empty
///                    for a cell that is as wide as its text
record SelectValue(String text, boolean placeholder, List<String> widths) implements Widget.Leaf, Styled, Paints {

    SelectValue {
        widths = List.copyOf(widths);
    }

    @Override
    public String cssType() {
        return "select-value";
    }

    @Override
    public Set<String> classes() {
        return placeholder ? Set.of("placeholder") : Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var widest = widest(style, context);
        if (text.isEmpty()) {
            // An empty field is a box with nothing in it and not a paragraph of
            // no characters: a measured leaf over an empty string still reports a
            // line's height, which would make a select with no placeholder taller
            // than one with a value.
            var empty = Box.of().style(style);
            return widest > 0 ? empty.size(Length.points((float) widest), style.height()) : empty;
        }
        var box = Box.text(context.paragraph(style, text), style.color()).style(style);
        return widest > 0 ? box.size(Length.points((float) widest), style.height()) : box;
    }

    /// The natural width of the widest label, rounded up to a whole point, or 0
    /// when there are none.
    private double widest(ComputedStyle style, Context context) {
        var widest = 0.0;
        for (var label : widths) {
            if (!label.isEmpty()) {
                var paragraph = context.paragraph(style, label);
                widest = Math.max(widest, paragraph.widthBetween(0, label.length()));
            }
        }
        return Math.ceil(widest);
    }
}
