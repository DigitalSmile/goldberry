package dev.goldberry.example.ui.media;

import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Video** screen: `video.kdl`'s `media-player` across the top, and the
/// cards that drive and report on the same player under it, with subtitles and
/// hardware decoding.
///
/// Read more: [`media-player`](https://goldberry.dev/docs/components/media.html#media-player).
///
/// @param context what the screen is built from
public record VideoChapter(GalleryContext context) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext buildContext) {
        return new MediaWall(
                MediaKind.VIDEO,
                context.model().videoPlayer(),
                null,
                context.documents().document("video.kdl"));
    }
}
