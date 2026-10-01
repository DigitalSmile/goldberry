/// `docs/core-widgets.md` §5's accordion — a `column` of `collapse` sections of which
/// one is open at a time, written as `accordion=#true` on the column.
///
/// It has no markup node of its own:
/// [dev.goldberry.widgets.panel.accordion.Accordion] is what a
/// `column` with the flag becomes, since only the container can hold a rule about its
/// siblings. The styled node still reports `column`, with an `accordion` class beside
/// the document's own (ADR-0166).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.panel.accordion;

import org.jspecify.annotations.NullMarked;
