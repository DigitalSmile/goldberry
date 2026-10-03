package dev.goldberry.example.ui.media;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.controls.toggle.Toggle;

/// The **Hardware decoding** card: the player's switch, and the decoder that plays
/// now.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
///
/// @param kind    which screen
/// @param on      whether the player decodes on the GPU's video engine
/// @param decoder the video decoder playing now, or a dash
/// @param desk    what the switch asks for
record HardwareCard(MediaKind kind, boolean on, String decoder, MediaDesk desk) implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        return new ShowcaseCard(
                        kind.id("hardware"),
                        "Hardware decoding",
                        "Decoding uses the GPU's video engine where there is one, and hands over to software when"
                                + " it cannot. Switching reopens the source where it was; the decoder line says"
                                + " which one plays.",
                        MediaDocs.CODEC)
                .of(
                        new Toggle("Decode on the GPU's video engine", on, desk::setHardware)
                                .id(kind.id("hardware-toggle")),
                        MediaLines.line("Decoder", decoder));
    }
}
