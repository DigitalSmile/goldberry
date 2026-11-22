/// Draws the lines along text: underline and line-through, in each of CSS's
/// five `text-decoration-style`s.
///
/// The paragraph says where a piece of a line is and in which face. This
/// package turns the face's metrics and the decoration's paint into ink. It is
/// not exported: the paragraph is its one caller.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
@NullMarked
package dev.goldberry.text.decoration;

import org.jspecify.annotations.NullMarked;
