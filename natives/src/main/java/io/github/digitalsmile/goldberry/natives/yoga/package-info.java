/// Yoga, the flexbox layout engine: owning wrappers over its nodes and configs, the
/// values a layout pass reads back, and the measure callback through which Yoga asks
/// a leaf for its size.
///
/// Exported to `:core` alone. A node's `YGNodeRef` never leaves this package, and
/// the binding class behind the wrappers is package-private. The measure callback is
/// the struct-by-value upcall ADR-0017 is about, and the measure probe is how it is
/// proven correct from C.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.yoga;

import org.jspecify.annotations.NullMarked;
