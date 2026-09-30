/// The open half of a `select`: `select-list`, the panel of options shown in a popup
/// window of its own.
///
/// It is a part with two owners, `select` and `text-input`'s autocomplete, so it sits
/// in a package of its own; that package is deliberately **not exported**, which
/// keeps the part styleable and not constructible from outside the module (ADR-0065,
/// ADR-0417). It is the sibling of `menu` rather than a use of it: the same drawing,
/// but a set of values rather than of commands.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.controls.selectlist;

import org.jspecify.annotations.NullMarked;
