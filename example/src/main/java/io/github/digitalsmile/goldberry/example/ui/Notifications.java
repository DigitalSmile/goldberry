package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.Spacer;
import io.github.digitalsmile.goldberry.widgets.overlay.message.Message;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **inline** half of the Overlays screen: `message`, which is part of the
/// layout and stays until the condition it describes does.
///
/// Not a toast. A toast floats over the window, stacks in a corner and goes on
/// its own; a banner sits in the flow, moves what is under it, and goes when the
/// application stops describing it ([ADR-0175]).
///
/// ## Three cards and not one widget
///
/// Each of the three below is its own [Widget.Stateful], and that is the point
/// rather than a division of a file: a masonry places by column height, so three
/// cards can go under three different columns where one tall widget can only go
/// under one. It also puts each piece of state with the card that owns it — the
/// hidden set only ever affects the resident banners, and the spawned list only
/// ever affects the stack — so neither card rebuilds when the other changes
/// (ADR-0222).
public final class Notifications {

    private Notifications() {}

    /// The three banner cards, in the order they are offered to the wall.
    public static List<Widget> cards() {
        return List.of(new Kinds(), new Summary(), new Stack());
    }

    /// A titled card, which is what every tile in this gallery is.
    static Widget card(String id, String title, List<Widget> content) {
        var children = new ArrayList<Widget>(content.size() + 1);
        children.add(new Text(title, Attributes.NONE.classes("card-title")));
        children.addAll(content);
        return new Card(List.copyOf(children), Attributes.NONE.id(id).classes("wall-card"));
    }

    private static Widget caption(String text) {
        return new Text(text, Attributes.NONE.classes("caption"));
    }

    /// §1.2's rule, drawn: each kind sets a **glyph** as well as a colour, so
    /// with the hues removed these are still four different pictures.
    ///
    /// Stateful only because the danger banner can be dismissed, which is the
    /// half of a banner a still picture cannot show: it goes because this card
    /// stopped describing it.
    record Kinds() implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new KindsState();
        }

        static final class KindsState extends State<Kinds> {

            private final EnumSet<Message.Kind> hidden = EnumSet.noneOf(Message.Kind.class);

            @Override
            public Widget build(BuildContext context) {
                var resident = new ArrayList<Widget>(6);
                resident.add(caption("Each kind sets a glyph as well as a colour: with the"
                        + " hues removed these are still four different drawings."));
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
                resident.add(new Column(banners, Attributes.NONE.id("notice-kinds")));
                if (hidden.isEmpty()) {
                    resident.add(caption("Dismiss the last one — it goes because this card"
                            + " stopped describing it, not because the widget hid itself."));
                } else {
                    resident.add(caption("Gone, and nothing is left where it was. Press Reset"
                            + " on the card below to describe it again."));
                }
                return card("kinds-card", "The four kinds", resident);
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

    /// §4's sentence, drawn: failures "register in the form's error summary".
    ///
    /// `Message.summary(errors)` **is** that summary — one banner with a line per
    /// failure, and an empty `Optional` when nothing is wrong, which is what keeps
    /// an application from drawing a box that says nothing.
    record Summary() implements Widget.Stateless {

        @Override
        public Widget build(BuildContext context) {
            return card(
                    "summary-card",
                    "A form's error summary",
                    List.of(
                            caption("One banner with a line per failure — and nothing at all when"
                                    + " nothing is wrong, which is why it hands back an Optional."),
                            new Column(
                                    List.of(Message.summary(List.of(
                                                    "A name is required", "A palantír answers between 1024 and 65535"))
                                            .orElseThrow()),
                                    Attributes.NONE.id("notice-summary"))));
        }
    }

    /// A stack that grows and shrinks — the half of a banner that is about the
    /// application rather than about the widget.
    record Stack() implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new StackState();
        }

        static final class StackState extends State<Stack> {

            private record Notice(int number, Message.Kind kind) {}

            private final List<Notice> notices = new ArrayList<>();

            private int spawned;

            @Override
            public Widget build(BuildContext context) {
                return card(
                        "stack-card",
                        "Raising one",
                        List.of(
                                caption("A banner arrives when the application describes one and goes"
                                        + " when it stops. Press a kind to add one; press its × to take"
                                        + " it away."),
                                bar(),
                                list()));
            }

            private Widget bar() {
                var buttons = new ArrayList<Widget>();
                for (var kind : Message.Kind.values()) {
                    var name = kind.name();
                    var label = name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
                    buttons.add(new Button(label, () -> spawn(kind))
                            .withAttributes(Attributes.NONE.id("spawn-" + kind.cssClass())));
                }
                buttons.add(new Spacer());
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
                    banners.add(new Message(
                                    notice.kind(),
                                    "Message " + notice.number() + " — dismiss it and it is gone,"
                                            + " because this list is what was describing it.")
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
