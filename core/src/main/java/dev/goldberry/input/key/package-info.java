/// The keys a toolkit names, the modifiers held with them, and a shortcut as an
/// accelerator table writes one — `Ctrl+S`, `Primary+Z`, `F5`.
///
/// Keys are the ones that *do* something rather than type something; what the
/// user typed arrives as text, already translated by the platform. A shortcut is
/// a value, so two parsed from the same text are equal and can key a map.
/// Exported to applications as one of input's parts, split by the role each
/// plays.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
@NullMarked
package dev.goldberry.input.key;

import org.jspecify.annotations.NullMarked;
