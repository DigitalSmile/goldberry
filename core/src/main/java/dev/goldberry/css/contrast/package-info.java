/// Legibility, measured: WCAG 2.1's contrast arithmetic, the floors
/// `docs/design-system.md` §1.2 states, and an audit that checks a theme against
/// them.
///
/// Exported because §10 lets an application swap every alias token, and a theme it
/// wrote is one nothing else can check (ADR-0241). The audit finds its pairs by the
/// `--gb-<name>-bg` / `--gb-<name>-text` convention, so tokens an application adds
/// are checked too, and it skips translucent colours rather than scoring them
/// badly.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.css.contrast;

import org.jspecify.annotations.NullMarked;
