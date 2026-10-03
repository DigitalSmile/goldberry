package dev.goldberry.widgets.text;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.attr.Bindable;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One run of text. It wraps at the width layout gives it, and nothing else
/// about it is decided in Java.
///
/// ```kdl
/// text style="title" "The Red Book"
/// text class="caption" "Marked in a hand that was not steady"
/// text bind="app.status" "checking…"
/// ```
///
/// In Java: `new Text("The Red Book").style(TextRank.TITLE)`, or
/// `Text.of("checking…", observable)` for a bound one.
///
/// The argument is what the text says. With `bind=`, the argument is the
/// fallback shown until the bound value answers, and a `null` value draws as
/// nothing rather than as the word `null`. The value is read at render, so a
/// change that lands between a build and a frame is in that frame.
///
/// Everything visual is the stylesheet's: this sets no size, no weight and no
/// colour. `style=` and `class=` are two spellings of one thing. `style="title"`
/// names a [TextRank] and is checked when the document inflates, so a typo is
/// refused where it is written; `class="title"` is the CSS spelling. Both put
/// the class `title` on the node, and a rule written `text.title` matches
/// either.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#text).
@Markup("text")
public record Text(String content, @Nullable Observable<?> source, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Text>, Bindable<Text> {

    public Text(String content) {
        this(content, null, Attributes.NONE);
    }

    public Text(String content, Attributes attributes) {
        this(content, null, attributes);
    }

    /// Text that follows a property. Equivalent to `text bind="…"`.
    public static Text of(Observable<?> source) {
        return new Text("", Objects.requireNonNull(source, "source"), Attributes.NONE);
    }

    /// The same, with a fallback shown until the property has a value, which is
    /// what `text bind="…" "fallback"` inflates to.
    public static Text of(String fallback, Observable<?> source) {
        return new Text(fallback, Objects.requireNonNull(source, "source"), Attributes.NONE);
    }

    public Text {
        Objects.requireNonNull(content, "content");
    }

    /// What this text says right now — the bound value, or the literal.
    ///
    /// Read at render rather than captured at build, so a change that arrives
    /// between a build and a frame is shown by that frame rather than the one
    /// after it. `null` in a property reads as the empty string: a value that has
    /// not loaded yet is nothing to draw, not the word "null".
    public String resolved() {
        if (source == null) {
            return content;
        }
        var value = source.get();
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public Text bound(Observable<?> source) {
        return new Text(content, source, attributes);
    }

    @Override
    public Text withAttributes(Attributes attributes) {
        return new Text(content, source, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public String cssType() {
        return "text";
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
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // A measured leaf: Yoga proposes a width, the paragraph wraps at it, and
        // the height that comes back is what sizes the box.
        return Box.text(context.paragraph(style, resolved()), style.color()).style(style);
    }

    /// Builds a `text` node from markup.
    ///
    /// A bound node keeps its argument as the fallback rather than refusing it:
    /// `text bind="user.name" "…"` is what a lenient registry shows for a path
    /// nothing answers yet, and it is what a designer laying out a screen wants
    /// to see.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var source = wiring.bound(node);
        var literal = Wiring.label(node);
        var attributes = ranked(Attributes.of(node), node.stringProperty("style"));
        return source == null ? new Text(literal, attributes) : new Text(literal, source, attributes);
    }

    /// `attributes` with `rank`'s class added, or unchanged when no `style=` was
    /// written.
    ///
    /// Added to the classes rather than kept as a component of its own, because
    /// the two spellings must be **one** thing below the markup: a rule written
    /// `text.title` has to match a `text style="title"`, and a widget carrying a
    /// rank the cascade could not see would be a second mechanism that looks like
    /// the first.
    private static Attributes ranked(Attributes attributes, @Nullable String rank) {
        return rank == null
                ? attributes
                : withClass(attributes, TextRank.of(rank).cssClass());
    }

    /// `attributes` with one more class, keeping the ones it had: a document may
    /// write both spellings, and `text style="title" class="muted"` means both.
    private static Attributes withClass(Attributes attributes, String added) {
        var classes = new java.util.LinkedHashSet<>(attributes.classes());
        classes.add(added);
        return attributes.classes(classes.toArray(String[]::new));
    }

    /// This text at one rank of the type scale: the Java spelling of `style="title"`.
    public Text style(TextRank rank) {
        return withAttributes(
                withClass(attributes, Objects.requireNonNull(rank, "rank").cssClass()));
    }
}
