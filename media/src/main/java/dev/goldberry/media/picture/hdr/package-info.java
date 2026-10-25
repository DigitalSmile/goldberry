/// HDR and wide-gamut pictures mapped to SDR BT.709 for CPU present: the
/// arithmetic GPU present's shader does, as the tables the media natives map a
/// converted picture through.
///
/// Not exported: [dev.goldberry.media.picture.YuvConverter] is the one caller.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
@NullMarked
package dev.goldberry.media.picture.hdr;

import org.jspecify.annotations.NullMarked;
