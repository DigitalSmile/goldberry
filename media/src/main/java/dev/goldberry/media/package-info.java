/// Audio and video over FFmpeg, driven from Java.
///
/// [dev.goldberry.media.MediaPlayer] plays a source's audio
/// and video, reports [dev.goldberry.media.PlayerStatus] values
/// and hands out pictures to draw: [dev.goldberry.media.picture.VideoPicture]s
/// converted to BGRA, or [dev.goldberry.media.picture.VideoPlanes] for
/// a view that converts them on the GPU
/// ([dev.goldberry.media.picture.PictureForm]).
/// [dev.goldberry.media.MediaProbe] opens a source through the
/// I/O SPI and lists its tracks without playing it, and every failure is a
/// [dev.goldberry.media.MediaError].
/// [dev.goldberry.media.MediaClock] is the Clock SPI a source
/// with no audio is timed against.
///
/// Exported to every module. Null-marked: every reference is non-null unless
/// annotated otherwise.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html#a-player).
@NullMarked
package dev.goldberry.media;

import org.jspecify.annotations.NullMarked;
