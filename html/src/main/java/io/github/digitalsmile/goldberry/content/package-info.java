/// What the two content views share as **API** — one interface, so far.
///
/// The distinction this package draws is the one ADR-0065 draws everywhere else: a
/// type an application *implements* or *hands over* is surface and is exported, and
/// a part it only styles is not. [io.github.digitalsmile.goldberry.content.ImageSource]
/// is the first: `markdown-view` and `html-view` both take one, and neither of them
/// owns it (ADR-0300).
///
/// Its neighbours — `content.inline`, `content.image`, `content.entity` — are how the
/// two views are *built*, and none of them is exported.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.content;

import org.jspecify.annotations.NullMarked;
