/// A decoded picture, in the form a view asked for: a
/// [dev.goldberry.media.picture.VideoPicture] converted to
/// BGRA, or [dev.goldberry.media.picture.VideoPlanes] as the
/// decoder produced them, for a view that converts on the GPU
/// ([dev.goldberry.media.picture.PictureForm]).
///
/// A package of its own because this is what a view draws, not the player that
/// makes it. Exported to every module. Null-marked.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
@NullMarked
package dev.goldberry.media.picture;

import org.jspecify.annotations.NullMarked;
