package dev.goldberry.example.ui.media;

import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Audio** screen: `audio.kdl`'s `audio-player` across the top, and the
/// cards that drive and report on the same player under it, with a decoder this
/// application wrote itself.
///
/// Read more: [`audio-player`](https://goldberry.dev/docs/components/media.html#audio-player).
///
/// @param context what the screen is built from
public record AudioChapter(GalleryContext context) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext buildContext) {
        var model = context.model();
        return new MediaWall(
                MediaKind.AUDIO,
                model.audioPlayer(),
                model.javaPcmDecoder(),
                context.documents().document("audio.kdl"));
    }
}
