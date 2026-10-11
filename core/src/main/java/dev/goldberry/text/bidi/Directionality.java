package dev.goldberry.text.bidi;

import java.util.Objects;

import dev.goldberry.layout.Direction;

/// Where a paragraph's base direction comes from, and whether its characters
/// keep their own.
///
/// ```java
/// var rtl = Directionality.of(Direction.RTL);                  // direction: rtl
/// var each = Directionality.of(Direction.LTR, UnicodeBidi.PLAINTEXT);
/// var paragraph = Paragraph.of(font, text, 0, 0, rtl);
/// ```
///
/// The base direction decides which side a line starts at, where the neutral
/// characters between two runs go, and which way a run of digits in it reads.
/// It is per paragraph, and a paragraph is a hard line: two lines of one text
/// may each find their own under [Source#FIRST_STRONG].
///
/// [#AUTO] is what a paragraph made with no direction at all takes, so that a
/// label of Arabic drawn with no stylesheet reads from the right. A box always
/// has a `direction`, `ltr` unless a rule says otherwise, and the cascade passes
/// [#of(Direction, UnicodeBidi)].
///
/// @param direction the base direction, or the fallback when [Source#FIRST_STRONG]
///                  finds no strong character
/// @param source    where the base direction comes from
public record Directionality(Direction direction, Source source) {

    /// Left to right, as CSS's initial `direction`.
    public static final Directionality LTR = new Directionality(Direction.LTR, Source.DIRECTION);

    /// Right to left.
    public static final Directionality RTL = new Directionality(Direction.RTL, Source.DIRECTION);

    /// The first strong character's direction, left to right when there is
    /// none: what [dev.goldberry.text.Paragraph#of(dev.goldberry.text.font.Font, String)]
    /// takes.
    public static final Directionality AUTO = new Directionality(Direction.LTR, Source.FIRST_STRONG);

    /// Where a paragraph's base direction comes from.
    public enum Source {

        /// The direction given: CSS's `unicode-bidi: normal`.
        DIRECTION,

        /// Each paragraph's first strong character, rules P2 and P3 of the
        /// bidirectional algorithm, and the direction given when it has none:
        /// `unicode-bidi: plaintext`.
        FIRST_STRONG,

        /// The direction given, for the paragraph and every character in it:
        /// `unicode-bidi: bidi-override`.
        OVERRIDE
    }

    public Directionality {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(source, "source");
    }

    /// `direction` as the base direction, each character keeping its own.
    public static Directionality of(Direction direction) {
        return Objects.requireNonNull(direction, "direction") == Direction.LTR ? LTR : RTL;
    }

    /// What a box with this `direction` and `unicode-bidi` gives its text.
    public static Directionality of(Direction direction, UnicodeBidi unicodeBidi) {
        Objects.requireNonNull(unicodeBidi, "unicodeBidi");
        return switch (unicodeBidi) {
            case NORMAL, EMBED, ISOLATE -> of(direction);
            case BIDI_OVERRIDE, ISOLATE_OVERRIDE -> new Directionality(direction, Source.OVERRIDE);
            case PLAINTEXT -> new Directionality(direction, Source.FIRST_STRONG);
        };
    }
}
