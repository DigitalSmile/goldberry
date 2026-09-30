/// A decoded picture, in the form a view asked for: a
/// [io.github.digitalsmile.goldberry.media.picture.VideoPicture] converted to
/// BGRA, or [io.github.digitalsmile.goldberry.media.picture.VideoPlanes] as the
/// decoder produced them, for a view that converts on the GPU
/// ([io.github.digitalsmile.goldberry.media.picture.PictureForm], ADR-0483).
///
/// Split from `…media` in ADR-0496: this is what a view draws, not the player
/// that makes it.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.picture;

import org.jspecify.annotations.NullMarked;
