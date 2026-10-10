/// Lottie, read and drawn in Java: the document model, its reader and the
/// renderer that draws it through the toolkit's own vector painter.
///
/// **Its own package, and not exported.** What an application holds is
/// [VectorAnimation][dev.goldberry.image.anim.VectorAnimation], beside the
/// frame-by-frame `Animation`; the records here are how a document is
/// understood, and a promise about them would be a promise about Lottie's JSON
/// schema, which is Bodymovin's to change.
///
/// The subset is the one Telegram allows in a `tgs` sticker, and somewhat more:
/// shape, null, solid and precomp layers with parenting; groups with their
/// transforms; rectangles, ellipses, stars, polygons and paths; flat and
/// gradient fills and strokes with caps, joins, miters and dashes; trims;
/// mattes and masks; keyframes held, eased on a solved cubic, moved along a
/// spatial curve, and paths blended vertex by vertex. An expression and an
/// image layer are refused; anything else unknown is passed over.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
@NullMarked
package dev.goldberry.image.lottie;

import org.jspecify.annotations.NullMarked;
