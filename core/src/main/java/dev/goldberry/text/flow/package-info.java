/// How a paragraph sits in the box it is drawn in: `white-space`, `text-overflow`,
/// `text-align` and `text-decoration`, and the [dev.goldberry.text.flow.TextFlow]
/// value that carries them together.
///
/// `white-space` decides whether a paragraph may break a line the author did not;
/// `text-overflow` decides what marks a line that was not broken and did not fit;
/// `text-align` decides where a line that fitted easily sits in the room left
/// over; and `text-decoration` draws a rule along it. All four are answered in
/// the paint, which is the only code that has both the line's width and the
/// box's.
///
/// These types are style, read by the paragraph and written by the cascade, so
/// they live beside the paragraph rather than under `css`: what they mean is a
/// fact about text layout, and the CSS spelling is one way in. The cascade
/// resolves the four properties separately, because CSS inherits some and not
/// others, and hands everything below it one `TextFlow`.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
@NullMarked
package dev.goldberry.text.flow;

import org.jspecify.annotations.NullMarked;
