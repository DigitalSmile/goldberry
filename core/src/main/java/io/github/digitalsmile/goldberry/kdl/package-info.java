/// `@NullMarked`, which puts this package under NullAway.
///
/// Inside a marked package every type is non-null unless it says `@Nullable`,
/// and the build fails on a violation. The annotation is the whole content of
/// this file: there is nothing package-specific to say about nullness, and a
/// paragraph pretending otherwise in every package would be padding.
///
/// Packages are marked one at a time on purpose. NullAway runs in
/// `OnlyNullMarked` mode, so an unmarked package is invisible to it and a marked
/// one is checked from the moment it opts in — which is the only way a codebase
/// this size adopts nullness at all (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.kdl;

import org.jspecify.annotations.NullMarked;
