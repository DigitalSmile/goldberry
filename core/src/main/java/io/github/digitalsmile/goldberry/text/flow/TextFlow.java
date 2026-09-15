package io.github.digitalsmile.goldberry.text.flow;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/// How a paragraph sits in the box it is drawn in: whether a line may break, what
/// marks one that did not fit, and where one that fitted easily sits.
///
/// One value rather than three fields on
/// [io.github.digitalsmile.goldberry.paint.Box.Text], for
/// [io.github.digitalsmile.goldberry.layout.Limits]'s reason — the three
/// are only ever read together, by the one method that draws a paragraph into a
/// box, and a painter that honoured one and not the others would be a bug nobody
/// would find.
///
/// ## Why the cascade keeps them apart and the paint puts them together
///
/// [io.github.digitalsmile.goldberry.css.ComputedStyle] carries the three
/// **separately**, because CSS inherits `white-space` and `text-align` and does
/// not inherit `text-overflow`, and a bundle cannot be part-inherited. It hands
/// out this record from
/// [io.github.digitalsmile.goldberry.css.ComputedStyle#textFlow()] once all three
/// have been resolved. So the split follows the one thing that actually differs
/// between them, and everything downstream of the cascade sees one value
/// ([ADR-0255], [ADR-0256]).
///
/// @param whiteSpace   whether a line too long for its box may be broken
/// @param textOverflow what marks a line that was not broken and did not fit
/// @param textAlign    where a line narrower than its box sits in it
/// @param decorations  the rules drawn along it, and usually none
public record TextFlow(
        WhiteSpace whiteSpace, TextOverflow textOverflow, TextAlign textAlign, Set<TextDecoration> decorations) {

    /// Wraps, marks nothing, sits at the leading edge, is undecorated — CSS's
    /// initial values for all four, and what every box in the catalog had before
    /// any of the properties existed.
    public static final TextFlow NORMAL =
            new TextFlow(WhiteSpace.NORMAL, TextOverflow.CLIP, TextAlign.START, TextDecoration.NONE);

    /// One line, cut with an ellipsis where it will not fit — the combination a
    /// label in a fixed-width cell wants, and the only one worth a name.
    public static final TextFlow ELLIPSIS =
            new TextFlow(WhiteSpace.NOWRAP, TextOverflow.ELLIPSIS, TextAlign.START, TextDecoration.NONE);

    public TextFlow {
        Objects.requireNonNull(whiteSpace, "whiteSpace");
        Objects.requireNonNull(textOverflow, "textOverflow");
        Objects.requireNonNull(textAlign, "textAlign");
        // Copied, so a flow is a value: this record is compared by equality and
        // kept as a key, and a set a caller can still mutate would change one.
        decorations = Set.copyOf(Objects.requireNonNull(decorations, "decorations"));
    }

    /// A flow that says nothing about decoration, which is every caller written
    /// before `text-decoration` was in the subset.
    public TextFlow(WhiteSpace whiteSpace, TextOverflow textOverflow, TextAlign textAlign) {
        this(whiteSpace, textOverflow, textAlign, TextDecoration.NONE);
    }

    /// A flow that says nothing about alignment either.
    public TextFlow(WhiteSpace whiteSpace, TextOverflow textOverflow) {
        this(whiteSpace, textOverflow, TextAlign.START, TextDecoration.NONE);
    }

    /// This, with a different `white-space`.
    public TextFlow whiteSpace(WhiteSpace value) {
        return new TextFlow(value, textOverflow, textAlign, decorations);
    }

    /// This, with a different `text-overflow`.
    public TextFlow textOverflow(TextOverflow value) {
        return new TextFlow(whiteSpace, value, textAlign, decorations);
    }

    /// This, with a different `text-align`.
    public TextFlow textAlign(TextAlign value) {
        return new TextFlow(whiteSpace, textOverflow, value, decorations);
    }

    /// This, with a different set of decorations.
    public TextFlow decorations(Set<TextDecoration> value) {
        return new TextFlow(whiteSpace, textOverflow, textAlign, value);
    }

    /// This, decorated with the lines named — `flow.decorations(UNDERLINE)`, or
    /// both of them.
    ///
    /// The varargs form is what a caller writes and the set is what the cascade
    /// hands over. It takes the first line **separately** so that there is no
    /// no-argument form of it: `decorations()` is the component's accessor, and an
    /// overload that could also mean "clear them" would be resolved by arity rather
    /// than by intent. Clearing is [#decorations(Set)] with [TextDecoration#NONE].
    public TextFlow decorations(TextDecoration first, TextDecoration... rest) {
        var lines = java.util.EnumSet.of(Objects.requireNonNull(first, "first"));
        lines.addAll(java.util.List.of(rest));
        return decorations(Set.copyOf(lines));
    }

    /// Whether anything is drawn along the line — what the painter asks before it
    /// reads the face's metrics at all.
    public boolean isDecorated() {
        return !decorations.isEmpty();
    }

    /// Whether `decoration` is one of them.
    public boolean has(TextDecoration decoration) {
        return decorations.contains(decoration);
    }

    /// Whether a line too long for its box may be broken.
    public boolean wraps() {
        return whiteSpace.wraps();
    }

    /// Whether an over-long line ends in an ellipsis.
    ///
    /// **False whenever the text wraps**, whatever `text-overflow` says: a
    /// wrapped line is never too long, so there is no place to put the mark. That
    /// is CSS's own rule and it is enforced here rather than in the painter, so
    /// that one reading of the pair cannot disagree with another's.
    public boolean ellipsises() {
        return !wraps() && textOverflow.marks();
    }

    @Override
    public String toString() {
        return "TextFlow[" + whiteSpace.name().toLowerCase(java.util.Locale.ROOT) + ", "
                + textOverflow.name().toLowerCase(java.util.Locale.ROOT) + ", "
                + textAlign.name().toLowerCase(java.util.Locale.ROOT) + ", "
                + decorationNames() + "]";
    }

    /// The decorations as CSS writes them — `none`, or `underline line-through` in
    /// the enum's own order so that two equal flows print the same.
    private String decorationNames() {
        if (decorations.isEmpty()) {
            return "none";
        }
        var names = new LinkedHashSet<String>();
        for (var decoration : TextDecoration.values()) {
            if (decorations.contains(decoration)) {
                names.add(decoration.cssName());
            }
        }
        return String.join(" ", names);
    }
}
