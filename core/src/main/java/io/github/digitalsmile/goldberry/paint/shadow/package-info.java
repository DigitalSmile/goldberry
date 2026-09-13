/// `@NullMarked`, which puts this package under NullAway.
///
/// Inside a marked package every type is non-null unless it says `@Nullable`,
/// and the build fails on a violation. The annotation is the whole content of
/// this file: there is nothing package-specific to say about nullness, and a
/// paragraph pretending otherwise in every package would be padding.
@NullMarked
package io.github.digitalsmile.goldberry.paint.shadow;

import org.jspecify.annotations.NullMarked;
