/// `video-view`'s GPU present, and the one place in this module that touches
/// the optional `:gpu` (`docs/gpu-plan.md`, phase 6; ADR-0484).
///
/// [io.github.digitalsmile.goldberry.media.view.gpu.GpuVideo] looks for `:gpu`
/// once and hands out a
/// [io.github.digitalsmile.goldberry.media.view.gpu.VideoPresenter]. Only the
/// package-private `GpuVideoPresenter` names `:gpu`'s types. Not exported: the
/// views in `…media.view` are its only callers (ADR-0496).
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.view.gpu;

import org.jspecify.annotations.NullMarked;
