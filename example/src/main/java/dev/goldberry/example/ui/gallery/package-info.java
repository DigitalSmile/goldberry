/// What every screen in the gallery is made of: the tab list in the book's order,
/// the card each section of the book is shown on, and the header each screen
/// opens with.
///
/// A card is one section of the guide. It carries a title, a short summary and a
/// link to that section, and [ShowcaseCard] is the one place the shape is written,
/// so a card built in Java and a card written in a document cannot drift apart.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
@NullMarked
package dev.goldberry.example.ui.gallery;

import org.jspecify.annotations.NullMarked;
