/// Reads the text properties whose values have parts: the `text-decoration`
/// shorthand and its longhands.
///
/// The values these readers produce live in `text.flow`, beside the paragraph
/// that draws them. This package is the CSS spelling of them and nothing else,
/// and it is not exported: the cascade is its one caller.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Text flow](https://goldberry.dev/docs/guide/styling.html#text-flow).
@NullMarked
package dev.goldberry.css.text;

import org.jspecify.annotations.NullMarked;
