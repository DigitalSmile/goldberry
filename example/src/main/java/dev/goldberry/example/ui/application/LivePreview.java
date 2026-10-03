package dev.goldberry.example.ui.application;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.text.Text;

/// A document a reader can edit, inflated again on every keystroke.
///
/// Three parts, top to bottom: the KDL in a `text-area`, a line saying what the
/// last attempt came to, and the widgets the last good version inflated to. A
/// broken edit leaves the preview as it was and shows the toolkit's message,
/// which is how a reloaded document behaves in a running application: the last
/// good version stays in force and the failure is reported once.
///
/// Read more: [Inflating a document](https://goldberry.dev/docs/applications.html#inflating-a-document).
///
/// @param id      the prefix of the parts' ids: `<id>-source`, `<id>-status`
///                and `<id>-preview`
/// @param source  the document the editor starts with
/// @param context what the preview's names resolve against
public record LivePreview(String id, String source, GalleryContext context) implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new LivePreviewState();
    }

    /// The editor's text, and what it last inflated to.
    static final class LivePreviewState extends State<LivePreview> {

        @SuppressWarnings("NullAway.Init") // made in initState()
        private MarkupPreview preview;

        @SuppressWarnings("NullAway.Init") // set in initState()
        private String text;

        private @Nullable Widget lastGood;

        private String status = "";

        private boolean refused;

        @Override
        protected void initState() {
            preview = MarkupPreview.of(widget().context());
            text = widget().source();
            apply();
        }

        private void edit(String value) {
            setState(() -> {
                text = value;
                apply();
            });
        }

        /// Inflates [#text], keeping the last good tree when it fails.
        private void apply() {
            switch (preview.inflate(text)) {
                case MarkupPreview.Inflated(var widget, var nodes) -> {
                    lastGood = widget;
                    refused = false;
                    status = nodes == 1 ? "Inflated one node." : "Inflated " + nodes + " nodes.";
                }
                case MarkupPreview.Refused(var message) -> {
                    refused = true;
                    status = message;
                }
            }
        }

        @Override
        public Widget build(BuildContext context) {
            var id = widget().id();
            var shown = lastGood == null
                    ? new Text("Nothing has inflated yet.", Attributes.NONE.classes("caption"))
                    : lastGood;
            return new Column(
                    List.of(
                            new TextArea(text, this::edit)
                                    .rows(3, 8)
                                    .withAttributes(Attributes.NONE
                                            .id(id + "-source")
                                            .classes("mono", "preview-source")
                                            .name("The document")),
                            new Text(
                                    status,
                                    Attributes.NONE
                                            .id(id + "-status")
                                            .classes(refused ? "preview-refused" : "preview-status")),
                            new Column(
                                    List.of(shown),
                                    Attributes.NONE.id(id + "-preview").classes("preview"))),
                    Attributes.NONE.classes("live-preview"));
        }
    }
}
