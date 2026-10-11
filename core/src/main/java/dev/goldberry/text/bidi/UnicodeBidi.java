package dev.goldberry.text.bidi;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How a box's own text takes its base direction: CSS's `unicode-bidi`.
///
/// ```css
/// .chat-message { unicode-bidi: plaintext }
/// ```
///
/// A box's text is always a paragraph of its own here, never inline content
/// flowing among its parent's, so the embedding and isolating values have
/// nothing to embed in or isolate from: [#EMBED] and [#ISOLATE] read as
/// [#NORMAL], and [#ISOLATE_OVERRIDE] as [#BIDI_OVERRIDE]. What is left are
/// three answers: the box's `direction` is the base direction ([#NORMAL]); each
/// paragraph finds its own from its first strong character ([#PLAINTEXT]); or
/// every character runs the box's way, whatever its script ([#BIDI_OVERRIDE]).
///
/// Not inherited, as in CSS.
public enum UnicodeBidi {

    /// The box's `direction` is the base direction. The initial value.
    NORMAL,

    /// As [#NORMAL], since a box's text embeds in nothing.
    EMBED,

    /// As [#NORMAL], since a box's text is already isolated from its parent's.
    ISOLATE,

    /// Every character runs in the box's `direction`.
    BIDI_OVERRIDE,

    /// As [#BIDI_OVERRIDE].
    ISOLATE_OVERRIDE,

    /// Each paragraph takes the direction of its first strong character, and
    /// the box's `direction` when it has none.
    PLAINTEXT;

    /// The keyword, or null when `name` is not one of the six.
    public static @Nullable UnicodeBidi parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "normal" -> NORMAL;
            case "embed" -> EMBED;
            case "isolate" -> ISOLATE;
            case "bidi-override" -> BIDI_OVERRIDE;
            case "isolate-override" -> ISOLATE_OVERRIDE;
            case "plaintext" -> PLAINTEXT;
            default -> null;
        };
    }
}
