/// Character references, resolved once on the way in.
///
/// Shared by both content halves and exported to neither: Markdown and HTML spell
/// `&amp;` the same way, and md4c's 2125-name table is the one copy of it either of
/// them needs (ADR-0294, ADR-0298).
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.content.entity;

import org.jspecify.annotations.NullMarked;
