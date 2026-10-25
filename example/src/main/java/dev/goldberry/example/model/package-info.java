/// Everything the showcase *knows*, and everything that happens to it, split by
/// the part of the window it belongs to.
///
/// [ShowcaseModel] and [ShowcaseActions] are the two halves as one value each;
/// the sub-packages hold the slices. Every slice is one `@Model` class with its
/// `@Actions` record nested inside it, and the paths they publish are disjoint —
/// the toolkit refuses two models claiming one name.
///
/// ## Why a class and not a record
///
/// A record's components are final, and a bound value has to be assignable — that
/// is the whole mechanism. So each slice's values are a class, and its *actions*
/// are the record, which is the half that genuinely holds nothing.
///
/// ## Why the actions are nested
///
/// So the fields can stay `private`. A nestmate reaches a private field and a
/// private method, so nesting the actions costs nothing in encapsulation — where
/// a sibling top-level class would have forced every field open to the package.
/// The build rewrites a write to a `@Bind` field wherever it appears, so an
/// assignment in the record notifies exactly as one in the class would.
///
/// Nesting is **scoping, not coupling**: a slice holds no reference to its
/// actions, mentions them in no signature, and would compile with them deleted.
/// The arrow still points one way.
///
/// ## Why here and not on the element
///
/// [dev.goldberry.widget.State] is for what the *UI* remembers — a scroll offset,
/// a caret, which tab is open — and it is right that those die with the widget
/// that owns them. This is the other kind: the leagues walked and the provisions
/// left are what the application is *about*, they outlive any particular screen,
/// and a second screen showing the same number reads the slice rather than a
/// copy of it.
///
/// ## Why the content is Middle-earth
///
/// Because a gallery whose every label reads "Item 1" is a gallery you cannot
/// read. Real prose wraps at awkward places, real names are of wildly different
/// lengths, and a table of six companions with a `Kindred` column shows a
/// sortable header doing something a column of `Row 1`… cannot.
///
/// Opened to `:core`, as every package holding a model is, so a model that was
/// not woven is bound reflectively at run time.
///
/// Marked for NullAway, as every package in the repository is.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@NullMarked
package dev.goldberry.example.model;

import org.jspecify.annotations.NullMarked;
