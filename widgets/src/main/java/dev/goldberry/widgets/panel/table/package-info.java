/// `docs/core-widgets.md` §10's `table` — a `list` with columns: one row per item,
/// and a header per column that can be resized and sorted by.
///
/// [dev.goldberry.widgets.panel.table.Table] is composed from
/// `list` rather than reimplemented, so it takes its selection, keyboard and
/// virtualization unchanged. Each
/// [dev.goldberry.widgets.panel.table.Column] is a key, a header,
/// a width and a cell factory, and
/// [dev.goldberry.widgets.panel.table.Sort] is the order the
/// application holds and hands down; the table sorts nothing itself. The header,
/// cells, grips and sort caret are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.panel.table;

import org.jspecify.annotations.NullMarked;
