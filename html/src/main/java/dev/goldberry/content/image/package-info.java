/// Images in a document: the part that draws one, and the application's answer about
/// where it came from.
///
/// Shared by both content views and exported for one of its two types:
/// [dev.goldberry.content.ImageSource] is what an
/// application implements, so it is part of the module's surface, while
/// [dev.goldberry.content.image.Picture] is a part — a CSS type a
/// stylesheet reaches and nothing constructs.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation.
///
/// Read more:
/// [Links, images and tasks](https://goldberry.dev/docs/components/content.html#links-images-and-tasks).
@NullMarked
package dev.goldberry.content.image;

import org.jspecify.annotations.NullMarked;
