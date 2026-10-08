/// A player's pictures drawn into an application's own textures, for its own
/// shaders: [dev.goldberry.media.gpu.PictureRenderer].
///
/// `video-view` shows a player in a window. This package is for a picture that
/// is not a widget's, such as a video a game samples on a card or a model, and
/// draws it into a whole texture or one level of one layer of one, the
/// `RenderTarget`s of `:gpu`'s API. The mapping of a picture onto what the GPU
/// draws stays inside this module.
///
/// Exported to every module, and the one exported package whose signatures name
/// `:gpu`'s types, which this module `requires transitive static`. An
/// application that uses it requires `:gpu` too, to have a frame to draw in.
/// Null-marked.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html#video-view).
@NullMarked
package dev.goldberry.media.gpu;

import org.jspecify.annotations.NullMarked;
