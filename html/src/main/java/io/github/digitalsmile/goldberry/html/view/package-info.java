/// `html-view` — the widget, its stylesheet, and the fold between them.
///
/// The same three parts the Markdown view has and the same division: the widget is a
/// value, the fold builds `column`, `row`, `text` and `button` out of the model, and
/// every visual decision is a rule in `html.css` that a theme drives (ADR-0295,
/// ADR-0298). The parts stay in here, which is ADR-0065's rule: a part is styleable
/// and not constructible.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.html.view;

import org.jspecify.annotations.NullMarked;
