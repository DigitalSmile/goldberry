package dev.goldberry.example.ui.richinput.composer;

import java.net.URI;
import java.util.List;

import dev.goldberry.example.ui.richinput.MarkedWriter;
import dev.goldberry.example.ui.richinput.suggest.Contacts;
import dev.goldberry.example.ui.richinput.suggest.Directories;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Style;

/// What the composer's conversation opens with: two days of messages, written
/// so that every kind of mark has been sent once before anything is typed.
public final class Conversation {

    /// The section a message sent now goes under.
    public static final String TODAY = "Today";

    /// The section before it.
    public static final String YESTERDAY = "Yesterday";

    /// Who the composer's messages are from.
    public static final String YOU = "You";

    private Conversation() {}

    /// The messages already there, oldest first.
    public static List<Message> opening() {
        var ann = Contacts.ALL.getFirst();
        var cal = Contacts.ALL.get(2);
        return List.of(
                new Message(
                        "m1",
                        YESTERDAY,
                        ann.name(),
                        MarkedWriter.start()
                                .plain("The ")
                                .with("release", Style.BOLD)
                                .plain(" notes are up: ")
                                .with("the draft", new MarkKind.Link(URI.create("https://goldberry.dev")))
                                .plain(". ")
                                .with("#release", Directories.topicKind("release"))
                                .text()),
                new Message(
                        "m2",
                        YESTERDAY,
                        "Bea Ortiz",
                        MarkedWriter.start()
                                .plain("Who broke ")
                                .with("main()", Style.CODE)
                                .plain("? ")
                                .with("It was me.", Style.SPOILER)
                                .text()),
                new Message(
                        "m3",
                        TODAY,
                        cal.name(),
                        MarkedWriter.start()
                                .with(ann.mentionText(), ann.kind())
                                .plain(" can we move it from ")
                                .with("Friday", Style.STRIKE)
                                .plain(" to ")
                                .with("Monday", Style.UNDERLINE)
                                .plain("? It is ")
                                .with("really", Style.ITALIC, Style.BOLD)
                                .plain(" close.")
                                .text()),
                new Message(
                        "m4",
                        TODAY,
                        "Dee Park",
                        MarkedWriter.start()
                                .with("/poll", Directories.commandKind("poll"))
                                .plain(" Lunch at noon? 🎉")
                                .text()));
    }
}
