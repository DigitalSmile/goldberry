package dev.goldberry.example.ui.richinput;

import java.net.URI;

import dev.goldberry.example.ui.richinput.suggest.Contacts;
import dev.goldberry.example.ui.richinput.suggest.Directories;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.text.mark.MarkedText;

/// The marked texts the Rich input screen's cards open with.
public final class Samples {

    /// A kind of the showcase's own: a highlighter's stroke, which grows as a
    /// style does and is drawn by `mark.highlight`.
    public static final MarkKind.Custom HIGHLIGHT = new MarkKind.Custom("highlight", "", Behaviour.STYLE);

    /// The address the samples link to.
    public static final URI SITE = URI.create("https://goldberry.dev");

    /// Every kind, each on words that say what it is: the six styles, a link,
    /// a mention, a topic, two kinds together, and words in Cyrillic and Greek,
    /// which the bundled faces draw, beside an emoji.
    public static final MarkedText EVERY_KIND = MarkedWriter.start()
            .with("Bold", Style.BOLD)
            .plain(", ")
            .with("italic", Style.ITALIC)
            .plain(", ")
            .with("underline", Style.UNDERLINE)
            .plain(", ")
            .with("strike-through", Style.STRIKE)
            .plain(", ")
            .with("code()", Style.CODE)
            .plain(" and a ")
            .with("spoiler", Style.SPOILER)
            .plain(".\nA ")
            .with("link", new MarkKind.Link(SITE))
            .plain(", a mention of ")
            .with(Contacts.ALL.getFirst().mentionText(), Contacts.ALL.getFirst().kind())
            .plain(" and a topic, ")
            .with("#design", Directories.topicKind("design"))
            .plain(".\n")
            .with("Bold italic", Style.BOLD, Style.ITALIC)
            .plain(", a ")
            .with("bold link", Style.BOLD, new MarkKind.Link(SITE))
            .plain(" and ")
            .with("struck code", Style.STRIKE, Style.CODE)
            .plain(".\nПривет, ")
            .with("мир", Style.BOLD)
            .plain(", and Γειά σου ")
            .with("κόσμε", Style.ITALIC)
            .plain(" 🚀")
            .text();

    /// A sentence with a highlighter stroke and one of each look the styles card
    /// restyles.
    public static final MarkedText RESTYLED = MarkedWriter.start()
            .plain("The ")
            .with("highlighted", HIGHLIGHT)
            .plain(" words, ")
            .with("bold", Style.BOLD)
            .plain(", ")
            .with("code()", Style.CODE)
            .plain(", a ")
            .with("link", new MarkKind.Link(SITE))
            .plain(" and ")
            .with(Contacts.ALL.get(1).mentionText(), Contacts.ALL.get(1).kind())
            .plain(".")
            .text();

    /// A message long enough to show a field growing, then scrolling.
    public static final MarkedText LONG = MarkedWriter.start()
            .plain("A field starts at ")
            .with("rows", Style.CODE)
            .plain(" lines and grows with its text to ")
            .with("max-rows", Style.CODE)
            .plain(", then scrolls. Press Shift+Enter or keep typing to watch it.")
            .text();

    private Samples() {}
}
