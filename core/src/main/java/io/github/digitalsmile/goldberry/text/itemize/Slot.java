package io.github.digitalsmile.goldberry.text.itemize;

/// Which face a run of text is shaped with.
///
/// `docs/ARCHITECTURE.md` §5 calls it "the emoji slot", and this is that word
/// made into a type. There are two of them because there are two faces: the one
/// the cascade chose, and the pictures ([ADR-0393]).
///
/// ## Why an enum and not a boolean
///
/// Because the list is going to grow. Splitting text by **script** is the same
/// operation with more answers — a Han run and a Latin run want different faces
/// for the same reason an emoji run does — and so is splitting it by direction,
/// which is the bidi work
/// [io.github.digitalsmile.goldberry.text.Paragraph] still approximates. A
/// `boolean emoji` would have to be replaced on the day either lands; an
/// enumerator is added to.
public enum Slot {

    /// The face the cascade resolved — `font-family`, and everything under it.
    TEXT,

    /// The emoji face, which ships as `goldberry-emoji` and may not be there.
    ///
    /// A run lands here because of what the **text** says, not because of what
    /// the face has: the itemizer reads Unicode's emoji properties, and whether
    /// anything can draw the result is a separate question asked by whoever is
    /// holding the fonts.
    EMOJI
}
