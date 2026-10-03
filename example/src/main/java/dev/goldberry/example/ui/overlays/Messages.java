package dev.goldberry.example.ui.overlays;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.overlay.message.Message;
import dev.goldberry.widgets.text.Text;

/// The `message` cards: a banner is part of the layout and stays until the
/// application stops describing it.
///
/// Three cards rather than one widget, so a masonry can put them under three
/// columns, and so each piece of state sits with the card that owns it.
///
/// Read more: [`message`](https://goldberry.dev/docs/components/overlays.html#message).
public final class Messages {

    /// The section every card here links.
    private static final DocLink MESSAGE = DocLink.to("components/overlays", "message");

    private Messages() {}

    /// The three banner cards, in the order they are offered to the wall.
    public static List<Widget> cards() {
        return List.of(new Kinds(), new Summary(), new Stack());
    }

    /// The four kinds, each with a glyph as well as a colour, and a danger banner
    /// that goes when it is dismissed.
    record Kinds() implements Widget.Stateful {

        private static final ShowcaseCard CARD = new ShowcaseCard(
                "kinds-card",
                "The four kinds",
                "A message is a banner in the layout: info, success, warning or danger, each with its own glyph"
                        + " as well as a colour. Dismiss the last one and it goes, because the card stopped"
                        + " describing it.",
                MESSAGE);

        @Override
        public State<?> createState() {
            return new KindsState();
        }

        /// Which kinds have been dismissed.
        static final class KindsState extends State<Kinds> {

            private final EnumSet<Message.Kind> hidden = EnumSet.noneOf(Message.Kind.class);

            @Override
            public Widget build(BuildContext context) {
                var banners = new ArrayList<Widget>(4);
                add(banners, new Message(Message.Kind.INFO, "Two riders were seen on the East Road at dusk."));
                add(
                        banners,
                        new Message(Message.Kind.SUCCESS, "The Company reached Rivendell and the Council is called."));
                add(
                        banners,
                        new Message(Message.Kind.WARNING, "The pass will close in five days.")
                                .actions(new Button("Take the low road", this::noted).styled("ghost")));
                add(
                        banners,
                        new Message(Message.Kind.DANGER, "The doors are watched; the way through the mines is barred.")
                                .dismiss(() -> hide(Message.Kind.DANGER)));
                return CARD.of(new Column(banners, Attributes.NONE.id("notice-kinds")));
            }

            private void add(List<Widget> into, Message banner) {
                if (!hidden.contains(banner.kind())) {
                    into.add(banner.id("kind-" + banner.kind().cssClass()));
                }
            }

            private void hide(Message.Kind kind) {
                setState(() -> hidden.add(kind));
            }

            private void noted() {
                setState(() -> {});
            }
        }
    }

    /// A form's error summary: one banner with a line per failure, and nothing at
    /// all when nothing is wrong.
    record Summary() implements Widget.Stateless {

        private static final ShowcaseCard CARD = new ShowcaseCard(
                "summary-card",
                "A form's error summary",
                "Message.summary builds one banner with a line per failure, and nothing at all when the list"
                        + " is empty, so a form never draws a box that says nothing.",
                MESSAGE);

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(new Column(
                    List.of(Message.summary(List.of("A name is required", "A palantír answers between 1024 and 65535"))
                            .orElseThrow()),
                    Attributes.NONE.id("notice-summary")));
        }
    }

    /// A stack the application grows and shrinks: a banner arrives when it is
    /// described and goes when it is not.
    record Stack() implements Widget.Stateful {

        private static final ShowcaseCard CARD = new ShowcaseCard(
                "stack-card",
                "Raising one",
                "A banner arrives when the application describes one and goes when it stops. Press a kind to"
                        + " add one, press its × to take it away, and Reset to clear them all.",
                MESSAGE);

        @Override
        public State<?> createState() {
            return new StackState();
        }

        /// The banners described so far, and a counter that gives each its key.
        static final class StackState extends State<Stack> {

            private record Notice(int number, Message.Kind kind) {}

            private final List<Notice> notices = new ArrayList<>();

            private int spawned;

            @Override
            public Widget build(BuildContext context) {
                return CARD.of(bar(), list());
            }

            private Widget bar() {
                var buttons = new ArrayList<Widget>();
                for (var kind : Message.Kind.values()) {
                    var name = kind.name();
                    var label = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
                    buttons.add(new Button(label, () -> spawn(kind))
                            .withAttributes(Attributes.NONE.id("spawn-" + kind.cssClass())));
                }
                buttons.add(new Button("Reset", this::clear)
                        .withAttributes(Attributes.NONE.id("clear-notices").classes("ghost")));
                return new Row(buttons.toArray(Widget[]::new))
                        .withAttributes(Attributes.NONE.id("notice-bar").classes("toolbar"));
            }

            private Widget list() {
                if (notices.isEmpty()) {
                    return new Text(
                            "No word has come.",
                            Attributes.NONE.id("notice-empty").classes("caption"));
                }
                var banners = new ArrayList<Widget>(notices.size());
                for (var notice : notices) {
                    banners.add(
                            new Message(notice.kind(), "Message " + notice.number() + ": dismiss it and it is gone.")
                                    .dismiss(() -> remove(notice))
                                    .id("notice-" + notice.number()));
                }
                return new Column(banners.toArray(Widget[]::new)).withAttributes(Attributes.NONE.id("notices"));
            }

            private void spawn(Message.Kind kind) {
                setState(() -> notices.add(new Notice(++spawned, kind)));
            }

            private void remove(Notice notice) {
                setState(() -> notices.remove(notice));
            }

            private void clear() {
                setState(notices::clear);
            }
        }
    }
}
