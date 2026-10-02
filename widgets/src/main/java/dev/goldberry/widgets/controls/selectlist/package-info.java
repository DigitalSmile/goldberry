/// The open half of a `select`: `select-list`, the panel of options shown in a popup
/// window of its own.
///
/// It is a part with two owners, `select` and `text-input`'s autocomplete, so it sits
/// in a package of its own; that package is deliberately **not exported**, which
/// keeps the part styleable and not constructible from outside the module. It is
/// the sibling of `menu` rather than a use of it: the same drawing, but a set of
/// values rather than of commands.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#select).
@NullMarked
package dev.goldberry.widgets.controls.selectlist;

import org.jspecify.annotations.NullMarked;
