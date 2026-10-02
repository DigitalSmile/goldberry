/// The bundled icon set: [dev.goldberry.icon.Icon] is one Lucide icon built
/// at one size, and [dev.goldberry.icon.SvgPath] reads the SVG path data an
/// icon is stored as.
///
/// Exported to applications. A widget names an icon in markup (`icon="plus"`)
/// and the application builds the `Icon` behind that name once, in its `Icons`
/// registry.
///
/// `@NullMarked`, which puts this package under NullAway: every type is non-null
/// unless it says `@Nullable`, and the build fails on a violation.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#icons).
@NullMarked
package dev.goldberry.icon;

import org.jspecify.annotations.NullMarked;
