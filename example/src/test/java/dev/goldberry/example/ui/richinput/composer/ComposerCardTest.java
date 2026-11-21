package dev.goldberry.example.ui.richinput.composer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.StyleElement;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.input.key.Shortcut;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;
import dev.goldberry.widgets.text.Text;

/// The composer card: the conversation opens in two day sections, the draft
/// opens formatted, `Enter` sends it into today's section as a `rich-text`
/// whose runs carry the marks' classes, and a contact or an emoji is chosen
/// with the keyboard alone.
class ComposerCardTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the draft opens with a word in each style")
    void draft() {
        var kinds = ComposerCard.DRAFT.marks().stream().map(mark -> mark.kind()).toList();
        assertTrue(kinds.contains(MarkKind.Style.BOLD));
        assertTrue(kinds.contains(MarkKind.Style.CODE));
        assertEquals("bold", ComposerCard.DRAFT.text().substring(13, 17));
        assertEquals("code()", ComposerCard.DRAFT.text().substring(27, 33));
    }

    @Test
    @DisplayName("the conversation opens in two day sections, every kind sent once")
    void opensInSections() {
        var opening = Conversation.opening();
        assertEquals(
                List.of(Conversation.YESTERDAY, Conversation.TODAY),
                opening.stream().map(Message::day).distinct().toList());
        var classes = opening.stream()
                .flatMap(message -> message.body().marks().stream())
                .map(mark -> mark.kind().className())
                .collect(Collectors.toSet());
        assertTrue(
                classes.containsAll(Set.of(
                        "bold",
                        "italic",
                        "underline",
                        "strike",
                        "code",
                        "spoiler",
                        "link",
                        "mention",
                        "topic",
                        "command")),
                "every kind once: " + classes);
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            var headers = walkFrom(session, ComposerCard.MESSAGES)
                    .map(Element::widget)
                    .filter(widget -> widget instanceof Text text
                            && text.attributes().classes().contains("composer-day"))
                    .map(widget -> ((Text) widget).content())
                    .distinct()
                    .toList();
            assertEquals(List.of(Conversation.YESTERDAY, Conversation.TODAY), headers);
            var spoiler = widget(session, ComposerCard.bodyId("m2"), RichText.class).runs().stream()
                    .filter(Run::spoiler)
                    .map(Run::text)
                    .toList();
            assertEquals(List.of("It was me."), spoiler);
            var mention = widget(session, ComposerCard.bodyId("m3"), RichText.class).runs().stream()
                    .filter(run -> run.classes().contains("mention"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(mention.onPress() != null, "a mention in a message can be pressed");
        }
    }

    @Test
    @DisplayName("a message sent goes under today, from you")
    void sentGoesUnderToday() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.ENTER);
            session.frame();
            assertEquals(
                    ComposerCard.DRAFT.text(),
                    sent(session, 1).runs().stream().map(Run::text).reduce("", String::concat));
            // In walk order: the last header before the sent message is today's,
            // and its row is marked as yours.
            var order = walkFrom(session, ComposerCard.MESSAGES)
                    .map(Element::widget)
                    .flatMap(widget -> switch (widget) {
                        case Text text
                        when text.attributes().classes().contains("composer-day") -> Stream.of("day:" + text.content());
                        case Column column when column.attributes().classes().contains("mine") -> Stream.of("mine");
                        case RichText text
                        when ComposerCard.bodyId("sent-1")
                                .equals(text.attributes().id()) -> Stream.of("sent");
                        default -> Stream.<String>empty();
                    })
                    .toList();
            assertEquals(List.of("day:" + Conversation.YESTERDAY, "day:" + Conversation.TODAY, "mine", "sent"), order);
        }
    }

    @Test
    @DisplayName("a colon and a few letters offer emoji, which go in as plain text")
    void emojiWithKeys() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.A, Modifiers.of(PrimaryModifier.current()));
            session.key(Key.BACKSPACE);
            session.type("Launch :roc");
            session.frame();
            session.key(Key.ENTER);
            session.key(Key.ENTER);
            session.frame();
            var message = sent(session, 1);
            assertEquals(
                    "Launch 🚀",
                    message.runs().stream()
                            .map(Run::text)
                            .reduce("", String::concat)
                            .strip());
            assertTrue(message.runs().stream().allMatch(run -> run.classes().isEmpty()), "an emoji is no mark");
        }
    }

    @Test
    @DisplayName("Enter sends the draft, which comes back as rich text with the marks as classes")
    void sends() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.ENTER);
            session.frame();
            var sent = sent(session, 1);
            var bold = sent.runs().stream()
                    .filter(run -> run.classes().contains("bold"))
                    .map(Run::text)
                    .toList();
            assertEquals(List.of("bold"), bold);
            // A link in a message can be pressed, so its run is `pressable` too.
            assertEquals(
                    Set.of("link", "pressable"),
                    sent.runs().stream()
                            .filter(run -> run.text().equals("link"))
                            .findFirst()
                            .orElseThrow()
                            .classes());
        }
    }

    @Test
    @DisplayName("the keyboard alone mentions somebody: @, a letter, Down, Enter, and Enter sends")
    void mentionsWithKeys() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Shortcut.primary(Key.A).toString());
            session.key(Key.BACKSPACE);
            session.type("Ask @a");
            session.frame();
            // Ann Lee and Ana Silva start with an a; Down marks the second.
            session.key(Key.DOWN);
            session.key(Key.ENTER);
            session.type("today");
            session.key(Key.ENTER);
            session.frame();
            var sent = sent(session, 1);
            var mention = sent.runs().stream()
                    .filter(run -> run.classes().contains("mention"))
                    .map(Run::text)
                    .toList();
            assertEquals(List.of("@Ana Silva"), mention);
            assertEquals(
                    "Ask @Ana Silva today", sent.runs().stream().map(Run::text).reduce("", String::concat));
        }
    }

    @Test
    @DisplayName("Escape closes the list, and Enter then sends the query as typed")
    void escapeThenSend() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.A, Modifiers.of(PrimaryModifier.current()));
            session.key(Key.BACKSPACE);
            session.type("@an");
            session.key(Key.ESCAPE);
            session.key(Key.ENTER);
            session.frame();
            var sent = sent(session, 1);
            assertEquals("@an", sent.runs().stream().map(Run::text).reduce("", String::concat));
            assertTrue(sent.runs().stream().noneMatch(run -> run.classes().contains("mention")));
        }
    }

    @Test
    @DisplayName("a draft with no formatting is read as Markdown when it is sent")
    void markdownOnSend() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            assertTrue(session.focus(ComposerCard.FIELD));
            session.key(Key.A, Modifiers.of(PrimaryModifier.current()));
            session.key(Key.BACKSPACE);
            session.type("ship it **today**, `x()`");
            session.key(Key.ENTER);
            session.frame();
            var sent = sent(session, 1);
            assertEquals(
                    "ship it today, x()", sent.runs().stream().map(Run::text).reduce("", String::concat));
            assertEquals(
                    List.of("today"),
                    sent.runs().stream()
                            .filter(run -> run.classes().contains("bold"))
                            .map(Run::text)
                            .toList());
            assertEquals(
                    List.of("x()"),
                    sent.runs().stream()
                            .filter(run -> run.classes().contains("code"))
                            .map(Run::text)
                            .toList());
        }
    }

    @Test
    @DisplayName("the composer reads and offers HTML on the clipboard")
    void installsHtml() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 1600)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(new ComposerCard())) {
            session.frame();
            // A headless session has no clipboard to paste from, so what is
            // checked here is that the format is there; reading a browser's
            // HTML is MarkedHtml's to test.
            var field = walk(session.byId("composer-column").orElseThrow())
                    .map(Element::widget)
                    .filter(RichInput.class::isInstance)
                    .map(RichInput.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertEquals(
                    List.of("text/html"),
                    field.clipboardFormats().stream().map(MarkedFormat::mime).toList());
        }
    }

    /// The `count`th message sent from the composer.
    private static RichText sent(Session session, int count) {
        return widget(session, ComposerCard.bodyId("sent-" + count), RichText.class);
    }

    private static Stream<Element> walkFrom(Session session, String id) {
        return walk(session.byId(id).orElseThrow());
    }

    /// The widget with this id, or the nearest one above it that is a `type`,
    /// since a widget's box carries its id too.
    private static <T> T widget(Session session, String id, Class<T> type) {
        StyleElement at = session.byId(id).orElseThrow(() -> new AssertionError("no #" + id));
        while (at instanceof Element element) {
            if (type.isInstance(element.widget())) {
                return type.cast(element.widget());
            }
            at = element.parent();
        }
        throw new AssertionError("#" + id + " is not a " + type.getSimpleName());
    }

    private static Stream<Element> walk(Element element) {
        return Stream.concat(Stream.of(element), element.children().stream().flatMap(ComposerCardTest::walk));
    }
}
