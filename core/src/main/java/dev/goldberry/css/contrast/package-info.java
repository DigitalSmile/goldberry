/// Legibility, measured: WCAG 2.1's contrast arithmetic, the floors the design
/// system states, and an audit that checks a theme against them.
///
/// Exported because an application may swap every alias token, and a theme it
/// wrote is one nothing else can check. The audit finds its pairs by the
/// `--gb-<name>-bg` / `--gb-<name>-text` convention, so tokens an application adds
/// are checked too, and it skips translucent colours rather than scoring them
/// badly.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#colour).
@NullMarked
package dev.goldberry.css.contrast;

import org.jspecify.annotations.NullMarked;
