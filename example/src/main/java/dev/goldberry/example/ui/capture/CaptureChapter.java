package dev.goldberry.example.ui.capture;

import java.util.List;

import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Capture** screen: a microphone card and a camera card, each over the
/// system's devices or a test signal, on a wall of wide columns, since each
/// card carries a picture, its readings, its metrics and a clip.
///
/// ```text
/// ┌──────────────────────────────────────────┐
/// │ CAPTURE                           Docs ↗ │
/// │ ┌──────────────┐ ┌──────────────┐        │
/// │ │ Microphone   │ │ Camera       │        │
/// │ └──────────────┘ └──────────────┘        │
/// └──────────────────────────────────────────┘
/// ```
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
///
/// @param context what the screen is built from
public record CaptureChapter(GalleryContext context) implements Widget.Stateless {

    /// The screen's name in the gallery.
    public static final String NAME = "capture";

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.wide(
                NAME,
                "Capture",
                "goldberry-mic records the system's microphones and goldberry-camera opens its cameras; both"
                        + " come with widgets. The devices are listed as the screen opens, nothing records until"
                        + " a card is started, and leaving the screen closes it.",
                CaptureDocs.SCREEN,
                List.of(
                        new MicrophoneCard(context.devices().microphones()),
                        new CameraCard(context.devices().cameras())));
    }
}
