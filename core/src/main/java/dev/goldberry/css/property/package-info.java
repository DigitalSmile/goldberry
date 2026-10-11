/// The property table: every property the stylesheet engine reads, whether it
/// inherits, which longhands a shorthand writes, and how to copy one property
/// from one computed style into another.
///
/// The engine reads a declaration in one place, `ComputedStyle`'s property
/// switch, which knows how to parse each value and nothing about the property
/// as a thing of its own. This table is that second half: what `inherit`,
/// `initial`, `unset`, `all`, `transition: all` and `@supports` need to know
/// about a property without parsing anything. Its tests hold it to the switch,
/// name for name.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#inheritance).
@NullMarked
package dev.goldberry.css.property;

import org.jspecify.annotations.NullMarked;
