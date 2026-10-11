package dev.goldberry.example.ui.gallery;

/// A card widget whose card needs a wide column: one built around a text area,
/// an editor, or anything a reader types several lines into.
///
/// A [Wall] deals these into a second wall of wide columns under its own, so a
/// text area is not squeezed into a column sized for a row of buttons. A card
/// written in a document, or built with [ShowcaseCard#wide()], says the same
/// thing with the class [ShowcaseCard#WIDE]; this is for the stateful card that
/// builds its card itself, which a wall cannot look inside.
///
/// Read more: [Masonry](https://goldberry.dev/docs/layout/masonry.html).
public interface WideCard {}
