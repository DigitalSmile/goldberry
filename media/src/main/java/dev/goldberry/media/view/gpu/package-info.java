/// `video-view`'s GPU present, and the one place in this module that touches
/// the optional `:gpu`.
///
/// [dev.goldberry.media.view.gpu.GpuVideo] looks for `:gpu`
/// once and hands out a
/// [dev.goldberry.media.view.gpu.VideoPresenter]. Only the
/// package-private `GpuVideoPresenter` and
/// [dev.goldberry.media.view.gpu.Pictures], the mapping of a picture onto the
/// layer's image, name `:gpu`'s types. Not exported: the views in
/// `…media.view` and the renderer in `…media.gpu` are its only callers.
/// Null-marked.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
@NullMarked
package dev.goldberry.media.view.gpu;

import org.jspecify.annotations.NullMarked;
