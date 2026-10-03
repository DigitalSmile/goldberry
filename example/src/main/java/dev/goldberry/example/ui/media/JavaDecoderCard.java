package dev.goldberry.example.ui.media;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.toggle.Toggle;

/// The **A decoder written in Java** card: the application's own
/// `DecoderProvider`, its switch, and a sample only it is asked about first.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
///
/// @param kind    which screen
/// @param enabled whether the decoder claims PCM
/// @param desk    what a press asks for
record JavaDecoderCard(MediaKind kind, boolean enabled, MediaDesk desk) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        return new ShowcaseCard(
                        kind.id("java-decoder"),
                        "A decoder written in Java",
                        "An application brings a codec as a DecoderProvider, asked before FFmpeg. This one decodes"
                                + " 16-bit PCM in Java: play the chime, then switch it off and the same file plays"
                                + " through FFmpeg. The Status card names the decoder.",
                        MediaDocs.CODEC)
                .of(
                        new Toggle("Decode PCM in Java", enabled, desk::setJavaDecoder).id(kind.id("java-toggle")),
                        new Button("Play the chime", () -> desk.open("java")).id(kind.id("java-chime")));
    }
}
