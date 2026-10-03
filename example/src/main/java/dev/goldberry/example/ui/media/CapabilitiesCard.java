package dev.goldberry.example.ui.media;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.media.MediaCapabilities;
import dev.goldberry.media.codec.DecoderProvider;
import dev.goldberry.media.platform.PlatformDecoders;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.text.Text;

/// The **This build** card: what the media module is, and what the libraries it
/// loaded decode and demux.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
///
/// @param kind         which screen
/// @param capabilities what the loaded libraries answer, or null when FFmpeg did
///                     not load
record CapabilitiesCard(MediaKind kind, @Nullable MediaCapabilities capabilities) implements Widget.Stateless {

    /// What the card says above the lines.
    static final String SUMMARY = "goldberry-media drives FFmpeg from Java, with no JNI. The published natives"
            + " decode royalty-free codecs; this is what the loaded libraries decode and demux, and which of the"
            + " system's own decoders are here.";

    @Override
    public Widget build(BuildContext context) {
        var card = new ShowcaseCard(kind.id("capabilities"), "This build", SUMMARY, MediaDocs.MODULE);
        var found = capabilities;
        if (found == null) {
            return card.of(
                    new Text("FFmpeg is not loaded, so nothing can play.", Attributes.NONE.classes("media-error")),
                    MediaLines.caption("Build the natives with ./gradlew :media:ffmpegBuild and run again. Picking a"
                            + " source shows the loader's own reason."));
        }
        var providers = PlatformDecoders.providers().stream()
                .map(DecoderProvider::name)
                .sorted()
                .toList();
        return card.of(
                MediaLines.line(
                        "Decodes",
                        String.join(", ", found.decoders().stream().sorted().toList())),
                MediaLines.line(
                        "Demuxes",
                        String.join(", ", found.demuxers().stream().sorted().toList())),
                MediaLines.line(
                        "Providers",
                        found.providers().isEmpty() ? "none on the module path" : String.join(", ", found.providers())),
                MediaLines.line(
                        "System decoders",
                        PlatformDecoders.available() && !providers.isEmpty()
                                ? String.join(", ", providers)
                                : "none: "
                                        + PlatformDecoders.unavailableReason().orElse("not available")));
    }
}
