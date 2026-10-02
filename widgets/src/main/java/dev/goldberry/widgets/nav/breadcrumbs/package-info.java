/// Breadcrumbs: the path to the current page, each step a way back up it.
///
/// [dev.goldberry.widgets.nav.breadcrumbs.Breadcrumbs] is the
/// trail and holds its one invariant: the last
/// [dev.goldberry.widgets.nav.breadcrumbs.Crumb] is where you are,
/// and is not a link. Past a length the middle collapses into a `…` that
/// opens a `menu` of the hidden crumbs rather than eliding characters. The separator,
/// the `…` and the row are parts.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html#breadcrumbs).
@NullMarked
package dev.goldberry.widgets.nav.breadcrumbs;

import org.jspecify.annotations.NullMarked;
