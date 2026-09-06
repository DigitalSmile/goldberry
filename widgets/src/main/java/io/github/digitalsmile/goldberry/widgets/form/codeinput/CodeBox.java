package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One box of a [CodeInput] — `code-box`, a **part**: styleable and not
/// constructible (ADR-0065).
///
/// A drawing rather than a field. §4 is explicit that "six boxes are a drawing,
/// not six fields", which is why this takes no focus, hears no keys and has no
/// semantics of its own: [CodeField] is the one Tab stop and the one textbox
/// anything is told about.
///
/// ## Two classes, because §8 has no pseudo-class for either fact
///
/// `filled` is whether this box has a character in it, and `active` is whether it
/// is the one the next character goes into. Neither is a state CSS can ask about
/// — `:checked` is "this one of a set is selected", which is not what a caret
/// being somewhere means — so both arrive as classes, exactly as `text-value`'s
/// `placeholder` does and for the same stated reason.
///
/// The focus ring is drawn on the `active` box by a rule that reads the *field's*
/// `:focus-visible`, because the field is what has the keyboard. §2.2's row asks
/// for the ring to be instant as it moves between boxes, which it is: nothing
/// transitions `outline`, and §1.7 rule 3 puts a focus ring outside motion
/// anyway.
///
/// @param character what to draw — one code point, already a bullet if the field
///                  masks, or empty
/// @param active    whether this is the box the next character goes into
public record CodeBox(String character, boolean active) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "code-box";
    }

    @Override
    public Set<String> classes() {
        if (character.isEmpty()) {
            return active ? Set.of("active") : Set.of();
        }
        return active ? Set.of("filled", "active") : Set.of("filled");
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (character.isEmpty()) {
            // Not a paragraph of no characters: a measured leaf over an empty
            // string still reports a line's height, and an empty box takes its
            // height from the stylesheet rather than from a line that is not
            // there. `text-value` declines the same thing for the same reason.
            return Box.of().style(style);
        }
        return Box.of().style(style).children(Box.text(context.paragraph(style, character), style.color()));
    }
}
