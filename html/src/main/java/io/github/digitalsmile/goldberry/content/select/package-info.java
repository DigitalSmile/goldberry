/// Selecting text in a rendered document.
///
/// The machinery both content views share, and none of it is exported: what an
/// application sees is a document it can drag across and copy from, plus
/// `selection-layer` and `word` as CSS types a stylesheet may restyle (ADR-0065,
/// ADR-0301).
///
/// The shape, because it is not obvious from the class names:
///
/// ```
/// SelectableDocument   the state: hears the pointer and the keyboard, owns both below
/// ├── SelectionLayer   an absolutely-positioned overlay that paints the highlight
/// └── (the folded document)
///     └── Word …       each one reports where it was laid out
/// WordGeometry         where every word is, what it says, and what it is part of
/// Caret                a word and a character in it
/// ```
///
/// The load-bearing decision is that a drag **repaints** rather than rebuilds: the
/// selection is mutable state the overlay's painter reads, so moving the pointer over
/// a six-hundred-word page costs one frame's paint rather than one frame's build.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.content.select;

import org.jspecify.annotations.NullMarked;
