/// The accordion: a `column` of `collapse` sections of which one is open at a
/// time, written as `accordion=#true` on the column.
///
/// It has no markup node of its own:
/// [dev.goldberry.widgets.panel.accordion.Accordion] is what a `column` with
/// the flag becomes, since only the container can hold a rule about its
/// siblings. The styled node still reports `column`, with an `accordion` class
/// beside the document's own, so a stylesheet written for columns still applies.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#collapse).
@NullMarked
package dev.goldberry.widgets.panel.accordion;

import org.jspecify.annotations.NullMarked;
