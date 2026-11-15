package dev.goldberry.example.ui.forms;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.forms.mention.Contacts;
import dev.goldberry.example.ui.forms.mention.MentionPicker;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkRun;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;

/// The `rich-input` card: a message composer whose sent messages are drawn by
/// `rich-text` from the same marks.
///
/// The draft opens with a word in each style, so the formatting is visible
/// before anything is typed. `Enter` sends and `Shift+Enter` breaks the line;
/// selecting words opens the formatting bar over them. `@` and a letter or two
/// offers people to mention ([MentionPicker]); `Up`, `Down` and `Enter` choose
/// one, which goes in as a single unit.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
public record ComposerCard() implements Widget.Stateful {

    /// The card's id.
    static final String ID = "composer-card";

    /// The composer's id.
    static final String FIELD = "composer";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "A field that takes formatting",
            "Select words for the formatting bar, or use Ctrl+B, Ctrl+I, Ctrl+Shift+M and Ctrl+K. "
                    + "Type @ to mention somebody. Enter sends, Shift+Enter breaks the line.",
            DocLink.to("components/forms", "keyboard"));

    /// What the composer opens with: a word in bold, one in italic, code and a
    /// link.
    static final MarkedText DRAFT = MarkedText.of("Ship it with bold, italic, code() and a link.")
            .mark(13, 17, MarkKind.Style.BOLD)
            .mark(19, 25, MarkKind.Style.ITALIC)
            .mark(27, 33, MarkKind.Style.CODE)
            .mark(40, 44, new MarkKind.Link(URI.create("https://goldberry.dev")));

    @Override
    public State<?> createState() {
        return new ComposerState();
    }

    /// The draft and what was sent, which live as long as the card does.
    static final class ComposerState extends State<ComposerCard> {

        private MarkedText draft = DRAFT;

        private final List<MarkedText> sent = new ArrayList<>();

        private final MentionPicker picker = new MentionPicker(Contacts.ALL, this::context);

        @Override
        public Widget build(BuildContext context) {
            var parts = new ArrayList<Widget>();
            for (var message : sent) {
                parts.add(bubble(message));
            }
            parts.add(new RichInput(draft, next -> draft = next)
                    .placeholder("Message")
                    .maxRows(4)
                    .onSubmit(this::send)
                    .mentions(picker.mentions())
                    .id(FIELD));
            return CARD.of(new Column(parts, Attributes.NONE.id("composer-column")));
        }

        @Override
        protected void dispose() {
            picker.close();
        }

        /// The picker, for a test.
        MentionPicker picker() {
            return picker;
        }

        private void send(MarkedText message) {
            if (message.text().isBlank()) {
                return;
            }
            setState(() -> {
                sent.add(message);
                draft = MarkedText.EMPTY;
            });
        }

        /// A sent message: a run per stretch of one set of marks, classed by
        /// the kinds over it, so `rich-text > run.bold` styles it.
        static RichText bubble(MarkedText message) {
            var runs = new ArrayList<Run>();
            for (var run : MarkRun.of(message.length(), message.marks())) {
                Set<String> classes =
                        run.kinds().stream().map(MarkKind::className).collect(Collectors.toSet());
                runs.add(new Run(message.text().substring(run.start(), run.end()), classes));
            }
            return new RichText(runs, Attributes.NONE.classes("composer-sent"));
        }
    }
}
