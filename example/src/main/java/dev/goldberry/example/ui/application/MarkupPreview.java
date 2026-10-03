package dev.goldberry.example.ui.application;

import java.util.List;

import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.kdl.KdlInflater;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.core.Column;

/// Text a reader typed, turned into widgets, or the reason it could not be.
///
/// The two steps are the toolkit's own: `KdlParser.parse` turns the text into
/// nodes, and a strict `KdlInflater` turns the nodes into widgets, resolving
/// every `press=`, `bind=` and `icon=` against the application's registries. A
/// failure at either step is a [Refused] carrying the message the toolkit wrote,
/// so a reader sees exactly what a developer would see in a log.
///
/// Read more: [Parsing and inflating](https://goldberry.dev/docs/guide/markup.html#parsing-and-inflating).
///
/// @param inflater what turns nodes into widgets
public record MarkupPreview(KdlInflater<Widget> inflater) {

    /// The longest refusal shown, in characters. The toolkit lists every bound
    /// name after a typo, which is the right thing in a log and a wall of text in
    /// a card.
    static final int MESSAGE_LIMIT = 220;

    /// What came of one attempt.
    public sealed interface Outcome permits Inflated, Refused {}

    /// The text parsed and every name in it resolved.
    ///
    /// @param widget the inflated tree: the one root, or a column of several
    /// @param nodes  how many top-level nodes the text held
    public record Inflated(Widget widget, int nodes) implements Outcome {}

    /// The text did not parse, or named something nothing registered.
    ///
    /// @param message what the toolkit said, shortened to [#MESSAGE_LIMIT]
    public record Refused(String message) implements Outcome {}

    /// A preview whose documents resolve against the gallery's own model, its
    /// actions, and the `plus` icon.
    public static MarkupPreview of(GalleryContext context) {
        return new MarkupPreview(
                Widgets.inflater(Icons.strict().bind("plus", context.plus()), context.model(), context.actions()));
    }

    /// Parses and inflates `source`.
    public Outcome inflate(String source) {
        try {
            var nodes = KdlParser.parse(source);
            if (nodes.isEmpty()) {
                return new Refused("The document has no nodes.");
            }
            var widgets = inflater.inflateAll(nodes);
            var root = widgets.size() == 1
                    ? widgets.getFirst()
                    : new Column(List.copyOf(widgets), Attributes.NONE.classes("preview-nodes"));
            return new Inflated(root, nodes.size());
        } catch (RuntimeException refused) {
            return new Refused(shorten(String.valueOf(refused.getMessage())));
        }
    }

    /// `message`, cut at [#MESSAGE_LIMIT] with an ellipsis.
    static String shorten(String message) {
        return message.length() <= MESSAGE_LIMIT
                ? message
                : message.substring(0, MESSAGE_LIMIT - 1).strip() + "…";
    }
}
