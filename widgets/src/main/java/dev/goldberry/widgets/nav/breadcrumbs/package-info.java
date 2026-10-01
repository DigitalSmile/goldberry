/// `docs/core-widgets.md` §6's `breadcrumbs` and its `crumb` — the path to the
/// current page, each step a way back up it.
///
/// [dev.goldberry.widgets.nav.breadcrumbs.Breadcrumbs] is the
/// trail and holds its one invariant: the last
/// [dev.goldberry.widgets.nav.breadcrumbs.Crumb] is where you are,
/// and is not a link (ADR-0306). Past a length the middle collapses into a `…` that
/// opens a `menu` of the hidden crumbs rather than eliding characters. The separator,
/// the `…` and the row are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.nav.breadcrumbs;

import org.jspecify.annotations.NullMarked;
