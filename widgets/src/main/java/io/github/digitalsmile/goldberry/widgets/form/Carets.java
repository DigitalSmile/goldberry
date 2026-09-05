package io.github.digitalsmile.goldberry.widgets.form;

/// What a text caret is drawn as, for the two controls that draw one
/// ([ADR-0253]).
///
/// `text-input` and `text-area` are in packages of their own and each had its own
/// copy of the number, the second carrying a comment saying it was the first's.
/// Two constants that must agree and cannot see each other is one constant with a
/// comment where the compiler should be.
///
/// ## Why it is not a `width` in a stylesheet
///
/// §8's subset gives a node its width through `width`, and a caret's box is set
/// by the field **after** the cascade — it is positioned and sized in the same
/// `render` that measures the text it sits in. A `caret { width: 3px }` would be
/// overwritten rather than honoured, which is worse than not being able to say
/// it. Read as a custom property ([ADR-0251]) it resizes the caret, which is what
/// an author writing one meant.
///
/// ## Why it is a token at all
///
/// One pixel is what every desktop draws, and a thicker caret is a real
/// low-vision aid — §13 lists that kind of switch. A number somebody has to fork
/// the toolkit to change is not a design system's default; it is a hard-coded
/// decision wearing one's clothes.
public final class Carets {

    /// The token an application overrides [#WIDTH] with.
    public static final String WIDTH_TOKEN = "--gb-caret-width";

    /// The default, in logical pixels.
    public static final double WIDTH = 1;

    private Carets() {}
}
