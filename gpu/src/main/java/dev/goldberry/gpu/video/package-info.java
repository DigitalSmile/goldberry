/// Video on the GPU: a layer that shows a player's pictures, as Y'CbCr planes
/// converted by the toolkit's shaders or as BGRA drawn as it is
/// (`docs/gpu-plan.md`, phase 6; ADR-0484).
///
/// **Exported to `:media` alone**, which `requires static` this module and shows
/// `video-view` through [dev.goldberry.gpu.video.VideoLayer]
/// when it is there. The vocabulary here is this module's own, not the media
/// engine's, since this module does not depend on `:media`: `:media` maps its
/// pictures onto [dev.goldberry.gpu.video.VideoImage].
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.video;
