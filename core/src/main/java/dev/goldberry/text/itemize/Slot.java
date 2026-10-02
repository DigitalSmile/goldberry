package dev.goldberry.text.itemize;

/// Which face a run of text is shaped with: the one the cascade chose, or the
/// emoji face.
///
/// The emoji face is a slot beside the text face rather than a member of a
/// fallback chain: a run is routed there because the text says it is a picture,
/// and the rest of the line stays in the family the stylesheet named.
///
/// An enum rather than a boolean because the list may grow. Splitting text by
/// script is the same operation with more answers, since a Han run and a Latin
/// run want different faces for the same reason an emoji run does, and so is
/// splitting it by direction, which is the bidi work a paragraph still
/// approximates. A `boolean emoji` would have to be replaced on the day either
/// lands; an enumerator is added to.
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
public enum Slot {

    /// The face the cascade resolved: `font-family`, and everything under it.
    TEXT,

    /// The emoji face, which ships as `goldberry-emoji` and may not be there.
    ///
    /// A run lands here because of what the text says, not because of what
    /// the face has: the itemizer reads Unicode's emoji properties, and whether
    /// anything can draw the result is a separate question asked by whoever is
    /// holding the fonts.
    EMOJI
}
