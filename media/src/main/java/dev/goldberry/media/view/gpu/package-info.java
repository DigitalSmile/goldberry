/// `video-view`'s GPU present, and the one place in this module that touches
/// the optional `:gpu`.
///
/// [dev.goldberry.media.view.gpu.GpuVideo] looks for `:gpu`
/// once and hands out a
/// [dev.goldberry.media.view.gpu.VideoPresenter]. Only the
/// package-private `GpuVideoPresenter` names `:gpu`'s types. Not exported: the
/// views in `…media.view` are its only callers. Null-marked.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
@NullMarked
package dev.goldberry.media.view.gpu;

import org.jspecify.annotations.NullMarked;
