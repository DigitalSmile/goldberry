/// Audio and video over FFmpeg, driven from Java (`docs/goldberry-media.md`).
///
/// [io.github.digitalsmile.goldberry.media.MediaPlayer] plays a source's audio
/// and video, reports [io.github.digitalsmile.goldberry.media.PlayerStatus] values
/// and hands out pictures to draw: [io.github.digitalsmile.goldberry.media.picture.VideoPicture]s
/// converted to BGRA, or [io.github.digitalsmile.goldberry.media.picture.VideoPlanes] for
/// a view that converts them on the GPU
/// ([io.github.digitalsmile.goldberry.media.picture.PictureForm]).
/// [io.github.digitalsmile.goldberry.media.MediaProbe] opens a source through the
/// I/O SPI and lists its tracks without playing it, and every failure is a
/// [io.github.digitalsmile.goldberry.media.MediaError].
/// [io.github.digitalsmile.goldberry.media.MediaClock] is the Clock SPI a source
/// with no audio is timed against. Progress is in `docs/media-plan.md`.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media;

import org.jspecify.annotations.NullMarked;
