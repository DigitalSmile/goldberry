package dev.goldberry.example.ui.richinput.composer;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.WideCard;
import dev.goldberry.example.ui.richinput.MarkedRuns;
import dev.goldberry.example.ui.richinput.MarkedWriter;
import dev.goldberry.example.ui.richinput.suggest.Directories;
import dev.goldberry.example.ui.richinput.suggest.Suggester;
import dev.goldberry.example.ui.richinput.suggest.SuggestionPicker;
import dev.goldberry.html.marked.MarkedHtml;
import dev.goldberry.markdown.marked.MarkedMarkdown;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAnchor;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.panel.list.ListView;
import dev.goldberry.widgets.panel.list.Selection;
import dev.goldberry.widgets.text.Text;

/// The composer: a conversation and the field a message is written in, the
/// way a messenger puts them together.
///
/// The conversation is a `list` in day sections, whose header pins to the top
/// as it scrolls, and each message is a `rich-text` drawn from the marks it was
/// sent with. A mention, a link, a topic and a command in a message can be
/// pressed, and the line under the list says what was; a spoiler is covered
/// until it is.
///
/// The draft opens with a word in each style. `Enter` sends and `Shift+Enter`
/// breaks the line; selecting words opens the formatting bar over them. `@`
/// offers people and `:` emoji ([SuggestionPicker]); `Up`, `Down` and `Enter`
/// choose. A paste from a browser keeps its formatting ([MarkedHtml#format()]),
/// and a draft with no formatting is read as Markdown when it is sent, so
/// `**today**` typed by hand arrives bold.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
public record ComposerCard() implements Widget.Stateful, WideCard {

    /// The card's id.
    public static final String ID = "composer-card";

    /// The composer's id.
    public static final String FIELD = "composer";

    /// The conversation's id.
    public static final String MESSAGES = "composer-messages";

    /// The line that says what was pressed.
    public static final String STATUS = "composer-status";

    /// The class each message's `rich-text` carries.
    public static final String BODY = "message-body";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "A message composer",
            "A conversation in day sections over a field that takes formatting. Select words for the bar, or use"
                    + " Ctrl+B, Ctrl+I, Ctrl+Shift+M and Ctrl+K. Type @ for people, : for emoji, or write"
                    + " **Markdown**. Enter sends.",
            DocLink.to("components/forms", "keyboard"));

    /// What the composer opens with: a word in bold, one in italic, code and a
    /// link.
    public static final MarkedText DRAFT = MarkedWriter.start()
            .plain("Ship it with ")
            .with("bold", Style.BOLD)
            .plain(", ")
            .with("italic", Style.ITALIC)
            .plain(", ")
            .with("code()", Style.CODE)
            .plain(" and a ")
            .with("link", new MarkKind.Link(URI.create("https://goldberry.dev")))
            .plain(".")
            .text();

    /// The element id of the message `id`'s body.
    public static String bodyId(String id) {
        return "message-" + id;
    }

    @Override
    public State<?> createState() {
        return new ComposerState();
    }

    /// The draft and the conversation, which live as long as the card does.
    static final class ComposerState extends State<ComposerCard> {

        private MarkedText draft = DRAFT;

        private final List<Message> messages = new ArrayList<>(Conversation.opening());

        private int sent;

        private String status = "Press a mention, a link or a topic in a message.";

        private final SuggestionPicker picker = new SuggestionPicker(triggers(), this::context);

        private static SequencedMap<String, Suggester> triggers() {
            var triggers = new LinkedHashMap<String, Suggester>();
            triggers.put("@", Suggester.of(Directories.PEOPLE));
            triggers.put(":", Suggester.of(Directories.EMOJI));
            return triggers;
        }

        @Override
        public Widget build(BuildContext context) {
            var list = new ListView<>(List.copyOf(messages), Message::id, this::row)
                    .selection(Selection.NONE)
                    .sections(Message::day, day -> new Text(day, Attributes.NONE.classes("composer-day")))
                    .id("composer-list");
            var conversation = new Panel(
                    List.of(new Scroll(List.of(list), ScrollAxis.VERTICAL, Attributes.NONE).anchor(ScrollAnchor.END)),
                    Attributes.NONE.id(MESSAGES));
            var field = new RichInput(draft, next -> draft = next)
                    .placeholder("Message")
                    .maxRows(4)
                    .onSubmit(this::send)
                    .mentions(picker.mentions())
                    .clipboardFormats(List.of(MarkedHtml.format()))
                    .id(FIELD);
            return CARD.of(new Column(
                    List.of(
                            conversation,
                            new Text(status, Attributes.NONE.id(STATUS).classes("caption")),
                            field),
                    Attributes.NONE.id("composer-column")));
        }

        private Widget row(Message message) {
            return new Column(
                    List.of(
                            new Text(message.author(), Attributes.NONE.classes("message-author")),
                            MarkedRuns.richText(
                                    message.body(),
                                    this::pressed,
                                    Attributes.NONE.id(bodyId(message.id())).classes(BODY))),
                    Conversation.YOU.equals(message.author())
                            ? Attributes.NONE.classes("message", "mine")
                            : Attributes.NONE.classes("message"));
        }

        private void pressed(MarkKind kind) {
            var said = switch (kind) {
                case MarkKind.Mention(var id) -> "Would open the profile of " + id;
                case MarkKind.Link(var uri) -> "Would open " + uri;
                case MarkKind.Custom(var name, var value, var _) -> "Would open the " + name + " " + value;
                case MarkKind.Style style -> "Pressed " + style.className();
            };
            setState(() -> status = said);
        }

        @Override
        protected void dispose() {
            picker.close();
        }

        /// The picker, for a test.
        SuggestionPicker picker() {
            return picker;
        }

        /// The messages, for a test.
        List<Message> messages() {
            return List.copyOf(messages);
        }

        private void send(MarkedText message) {
            if (message.text().isBlank()) {
                return;
            }
            var formatted = message.isPlain() ? MarkedMarkdown.read(message.text()) : message;
            setState(() -> {
                sent++;
                messages.add(new Message("sent-" + sent, Conversation.TODAY, Conversation.YOU, formatted));
                draft = MarkedText.EMPTY;
            });
        }
    }
}
