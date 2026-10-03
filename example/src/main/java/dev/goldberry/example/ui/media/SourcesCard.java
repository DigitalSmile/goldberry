package dev.goldberry.example.ui.media;

import java.util.List;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.select.Select;

/// The **Sources** card: the bundled samples, a file from disk, and a line saying
/// what the last pick does or why it failed.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
///
/// @param kind    which screen
/// @param chosen  the sample picked last, or empty
/// @param message what the last pick does, or why it failed
/// @param dialogs whether this window can ask for a file
/// @param desk    what a press asks for
record SourcesCard(MediaKind kind, String chosen, String message, boolean dialogs, MediaDesk desk)
        implements Widget.Stateless {

    @Override
    public Widget build(BuildContext context) {
        var options = kind.samples().stream()
                .map(sample -> new Option(sample.key(), sample.title()))
                .toArray(Option[]::new);
        var summary =
                switch (kind) {
                    case AUDIO ->
                        "open takes a Source of a path or a URI and returns at once. Pick a bundled clip, a live"
                                + " stream, one served over HTTP or a file from disk; the last two samples fail on"
                                + " purpose.";
                    case VIDEO ->
                        "open takes a Source of a path or a URI and returns at once. Pick a bundled clip, one"
                                + " served over HTTP or a file from disk. The H.264 file plays only where the"
                                + " system's own decoders are.";
                };
        return new ShowcaseCard(kind.id("sources"), "Sources", summary, MediaDocs.PLAYER)
                .of(
                        new Select(chosen, desk::open, options)
                                .placeholder("Choose a source…")
                                .id(kind.id("source")),
                        MediaLines.actions(List.of(
                                new Button("Open a file…", desk::openFile)
                                        .disabled(!dialogs)
                                        .id(kind.id("open-file")),
                                new Button("Close", desk::closeSource).id(kind.id("close")))),
                        MediaLines.caption(message).id(kind.id("source-note")));
    }
}
