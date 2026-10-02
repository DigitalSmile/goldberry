/// `@media`: the conditions a block of rules applies under, and the window and
/// desktop facts they are asked about.
///
/// [dev.goldberry.css.media.MediaQueries] reads a prelude into a
/// [dev.goldberry.css.media.MediaCondition], every rule carries one, and the
/// resolver skips the rules whose condition does not hold under its
/// [dev.goldberry.css.media.MediaContext]. The renderer moves the context when
/// the window resizes, the desktop's theme changes or motion is reduced, and
/// the cascade runs again only when some condition's answer changed.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Media queries](https://goldberry.dev/docs/guide/styling.html#media-queries).
@NullMarked
package dev.goldberry.css.media;

import org.jspecify.annotations.NullMarked;
