/// Images in a document: the part that draws one, and the application's answer about
/// where it came from.
///
/// Shared by both content views and exported for one of its two types:
/// [io.github.digitalsmile.goldberry.content.ImageSource] is what an
/// application implements, so it is part of the module's surface, while
/// [io.github.digitalsmile.goldberry.content.image.Picture] is a part — a CSS type a
/// stylesheet reaches and nothing constructs (ADR-0065, ADR-0300).
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.content.image;

import org.jspecify.annotations.NullMarked;
