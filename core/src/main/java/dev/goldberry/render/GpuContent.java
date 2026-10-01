package dev.goldberry.render;

/// Something the GPU draws into a window rather than the CPU rasterizing it: a
/// 3D view, a video picture (`docs/gpu-plan.md`, D4; ADR-0481).
///
/// **Opaque to `:core`.** `:core` cannot name how such content is drawn,
/// because drawing it takes `:gpu`'s device and frames, and `:gpu` depends on
/// `:core` rather than the other way round. So this is only an identity: a
/// painter places one with
/// [Frame#gpuLayer][dev.goldberry.paint.Frame#gpuLayer], the
/// frame records where, and the compositor, which is `:gpu`'s, draws it.
///
/// **Implemented through `:gpu`'s `GpuLayer`**, which extends this and says how
/// the content renders. A compositor draws nothing for content that is not one,
/// so implementing this interface directly places a layer that stays empty.
///
/// Compared by identity: the same object placed on two frames is the same layer,
/// and keeps what the compositor holds for it between them.
public interface GpuContent {}
