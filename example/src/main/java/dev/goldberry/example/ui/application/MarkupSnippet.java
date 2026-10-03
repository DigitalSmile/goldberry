package dev.goldberry.example.ui.application;

import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.text.Text;

/// A line of markup inflated once, when it is mounted, and placed among widgets
/// built in Java.
///
/// Once rather than per build: a document that does not change is parsed and
/// inflated once, and the tree it became is an ordinary widget from then on.
///
/// Read more: [How Java and markup compose](https://goldberry.dev/docs/guide/markup.html#how-java-and-markup-compose).
///
/// @param source  the markup, one root node
/// @param context what its names resolve against
public record MarkupSnippet(String source, GalleryContext context) implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new SnippetState();
    }

    /// The inflated tree, made in [#initState()] and again only when the source
    /// changes.
    static final class SnippetState extends State<MarkupSnippet> {

        @SuppressWarnings("NullAway.Init") // inflated in initState()
        private Widget inflated;

        @Override
        protected void initState() {
            inflated = inflate();
        }

        @Override
        protected void didUpdateWidget(MarkupSnippet previous) {
            if (!previous.source().equals(widget().source())) {
                inflated = inflate();
            }
        }

        private Widget inflate() {
            return switch (MarkupPreview.of(widget().context()).inflate(widget().source())) {
                case MarkupPreview.Inflated(var widget, var _) -> widget;
                case MarkupPreview.Refused(var message) ->
                    new Text(message, Attributes.NONE.classes("preview-refused"));
            };
        }

        @Override
        public Widget build(BuildContext context) {
            return inflated;
        }
    }
}
