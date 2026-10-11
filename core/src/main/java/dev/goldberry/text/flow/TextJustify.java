package dev.goldberry.text.flow;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Where a justified line takes its extra room: CSS's `text-justify`.
///
/// Read only when `text-align` is `justify`. A line is respaced at its word
/// gaps, so `auto` and `inter-word` are the same thing here, and `none` turns
/// justification off and leaves the line at its start. `inter-character` is
/// not read: it would put room between the letters of a word, which is
/// `letter-spacing`'s job.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
public enum TextJustify {

    /// The word gaps take the room. The initial value.
    AUTO,

    /// No justification: a `justify` line sits at its start.
    NONE,

    /// The word gaps take the room, as `auto` does.
    INTER_WORD;

    /// The keyword, or null: `auto`, `none` or `inter-word`.
    public static @Nullable TextJustify parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "none" -> NONE;
            case "inter-word" -> INTER_WORD;
            default -> null;
        };
    }
}
