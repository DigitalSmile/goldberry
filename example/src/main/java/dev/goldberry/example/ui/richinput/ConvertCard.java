package dev.goldberry.example.ui.richinput;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.WideCard;
import dev.goldberry.html.marked.MarkedHtml;
import dev.goldberry.markdown.marked.MarkedMarkdown;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.text.Text;

/// What a field holds, said four ways as it is typed: Markdown, HTML, a
/// messenger's entities, and a `rich-text` drawn from the same marks. A text
/// area takes Markdown the other way, into the field.
///
/// Read more: [`markdown-view`](https://goldberry.dev/docs/components/content.html#markdown-view).
public record ConvertCard() implements Widget.Stateful, WideCard {

    /// The card's id.
    public static final String ID = "rich-convert-card";

    /// The field's id.
    public static final String FIELD = "rich-convert";

    /// The output's id.
    public static final String OUTPUT = "rich-convert-output";

    /// The choice of output's id.
    public static final String CHOICE = "rich-convert-choice";

    /// The Markdown text area's id.
    public static final String SOURCE = "rich-convert-markdown";

    /// The button that reads it.
    public static final String READ = "rich-convert-read";

    /// The rendered preview's id.
    public static final String PREVIEW = "rich-convert-preview";

    /// What the output can show.
    public enum View {
        MARKDOWN("markdown", "Markdown"),
        HTML("html", "HTML"),
        ENTITIES("entities", "Entities");

        private final String value;

        private final String label;

        View(String value, String label) {
            this.value = value;
            this.label = label;
        }

        /// The view a choice's value names.
        static View of(String value) {
            for (var view : values()) {
                if (view.value.equals(value)) {
                    return view;
                }
            }
            throw new IllegalArgumentException("no view is called " + value);
        }

        /// `text` in this view.
        public String render(MarkedText text) {
            return switch (this) {
                case MARKDOWN -> MarkedMarkdown.write(text);
                case HTML -> MarkedHtml.write(text);
                case ENTITIES -> Entities.describe(text);
            };
        }
    }

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Markdown, HTML and entities",
            "Type and format in the field: its marks are written out as Markdown, as HTML, or as a messenger's"
                    + " entities, and drawn again as rich-text. Write Markdown below and read it into the field.",
            DocLink.to("components/content", "markdown-view"));

    @Override
    public State<?> createState() {
        return new ConvertState();
    }

    /// The field's text, the view, and the Markdown waiting to be read.
    static final class ConvertState extends State<ConvertCard> {

        private MarkedText value = Samples.EVERY_KIND;

        private View view = View.MARKDOWN;

        private String markdown = "Read **this**, _underlined_, ~~struck~~ and `code`, ||hidden||, and"
                + " [a link](https://goldberry.dev).";

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(
                    new RichInput(value, next -> setState(() -> value = next))
                            .maxRows(5)
                            .id(FIELD),
                    new Segmented(
                            view.value,
                            List.of(
                                    new Option(View.MARKDOWN.value, View.MARKDOWN.label),
                                    new Option(View.HTML.value, View.HTML.label),
                                    new Option(View.ENTITIES.value, View.ENTITIES.label)),
                            null,
                            next -> setState(() -> view = View.of(next)),
                            false,
                            Attributes.NONE.id(CHOICE)),
                    new Text(view.render(value), Attributes.NONE.id(OUTPUT).classes("mono", "rich-convert-output")),
                    new Text("As rich-text", Attributes.NONE.classes("caption")),
                    MarkedRuns.richText(value, null, Attributes.NONE.id(PREVIEW).classes("message-body")),
                    new Text("Markdown in", Attributes.NONE.classes("caption")),
                    new TextArea(markdown, next -> markdown = next)
                            .rows(2, 4)
                            .withAttributes(Attributes.NONE.id(SOURCE).classes("mono")),
                    new Row(new Button("Read into the field", this::read).withAttributes(Attributes.NONE.id(READ)))
                            .withAttributes(Attributes.NONE.classes("toolbar")));
        }

        private void read() {
            setState(() -> value = MarkedMarkdown.read(markdown));
        }

        /// The field's text, for a test.
        MarkedText value() {
            return value;
        }
    }
}
