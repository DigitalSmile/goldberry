package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.Spacer;
import io.github.digitalsmile.goldberry.widgets.overlay.message.Message;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;

/// The **Notifications** screen: `docs/core-widgets.md` §7's `message`, and the
/// two things a still picture of one cannot show.
///
/// A banner is easy to photograph and hard to *demonstrate*, because everything
/// interesting about it is a change: it arrives, and it goes away. Both are the
/// application's doing — which is the whole reason this screen is Java where the
/// Overlays screen beside it is a document.
///
/// ## Why the buttons had to be here
///
/// A `message` takes no `bind=`
/// ([ADR-0175](../../../../../../../book/src/adr/0175-a-banner-says-its-kind-twice.md)):
/// what changes about a banner is whether it is *there*, and a bound one would be
/// present and empty when the value was blank — §8's subset has no `display`, so
/// nothing can take itself out of a layout. Whatever describes it can, and here
/// that is [NotificationsState]'s list.
///
/// So the buttons are not a convenience for the demo. They are the demonstration:
/// pressing one adds a description and the banner rises into place over 160ms,
/// pressing its × fades it over 100ms and *then* takes the description away.
///
/// The order is the widget's and is what makes the exit possible at all: nothing
/// here holds a departing banner, so the × has to fade the thing while it is
/// still described and tell this screen afterwards.
///
/// ## And why `toast` is still a different widget
///
/// Everything on this screen is **part of the layout**: the banners push the
/// caption below them down, and they stay until somebody closes them. A toast
/// would float over the window, stack in a corner and time out on its own. §7
/// separates the two on exactly that line, and having them on one screen is what
/// will make the difference visible when the second one exists.
public record Notifications() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new NotificationsState();
    }

    /// The list of banners the buttons have spawned.
    ///
    /// Ordinary application state, and deliberately so: this is what the toolkit
    /// asks an application to own, and the screen is here to show how little it
    /// amounts to.
    static final class NotificationsState extends State<Notifications> {

        /// One spawned banner. The number is its identity — it is the key, so a
        /// banner dismissed from the middle of the list does not make the ones
        /// under it inherit its element and its half-finished arrival.
        private record Notice(int number, Message.Kind kind) {
        }

        private final List<Notice> notices = new ArrayList<>();

        /// Which of the four resident banners have been dismissed.
        ///
        /// The top group is a reference row rather than a list, so "dismissed"
        /// here means "this screen stops describing it" — which is the same
        /// sentence as for the spawned ones and the only one there is. `Reset`
        /// brings them back.
        private final java.util.EnumSet<Message.Kind> hidden =
                java.util.EnumSet.noneOf(Message.Kind.class);

        /// Never reused, so a key is never reused either. A counter and not
        /// `notices.size()`: two banners spawned after one is dismissed would
        /// otherwise share a number.
        private int spawned;

        @Override
        public Widget build(BuildContext context) {
            var rows = new ArrayList<Widget>();
            rows.add(new Text("Notifications", Attributes.NONE.classes("screen-title")));
            rows.add(new Text("An inline `message` is part of the layout and stays until the"
                    + " condition it describes does. It is not a toast: a toast floats over"
                    + " the window, stacks in a corner and goes on its own.",
                    Attributes.NONE.classes("prose")));

            rows.add(new Text("The four kinds", Attributes.NONE.classes("screen-title")));
            rows.add(new Text("Each kind sets a glyph as well as a colour, which is what"
                    + " §1.2 asks for: with the hues removed these are still four different"
                    + " drawings.", Attributes.NONE.classes("caption")));
            rows.add(kinds());

            rows.add(new Text("A form's error summary", Attributes.NONE.classes("screen-title")));
            rows.add(new Text("§4 says failures \"register in the form's error summary\"."
                    + " `Message.summary(controller.errors())` is that summary — one banner"
                    + " with a line per failure, and nothing at all when nothing is wrong.",
                    Attributes.NONE.classes("caption")));
            rows.add(summary());

            rows.add(new Text("Spawning one", Attributes.NONE.classes("screen-title")));
            rows.add(new Text("A banner arrives when the application describes one and goes"
                    + " when it stops. Press a kind to add one; press its × to take it away.",
                    Attributes.NONE.classes("caption")));
            rows.add(bar());
            rows.add(spawnedList());

            return new Column(rows.toArray(Widget[]::new))
                    .withAttributes(Attributes.NONE.id("screen-notifications"));
        }

        /// The four kinds, one under another — the picture the gallery golden
        /// takes. The warning carries an action link and the danger a ×, so the
        /// two things §7 makes optional are both in the image.
        private Widget kinds() {
            var resident = new ArrayList<Widget>(4);
            add(resident, new Message(Message.Kind.INFO,
                    "Two devices are signed in to this account."));
            add(resident, new Message(Message.Kind.SUCCESS, "Your changes were published."));
            add(resident, new Message(Message.Kind.WARNING, "This session ends in five minutes.")
                    .actions(new Button("Stay signed in", this::noted).styled("ghost")));
            // The one × in this group, and it does the real thing: the banner
            // fades over §1.7's `fast` and *then* this screen is told, at which
            // point `hidden` stops describing it. Press `Reset` to bring it back.
            add(resident, new Message(Message.Kind.DANGER,
                    "Could not save: port 80 is already in use.")
                    .dismiss(() -> hide(Message.Kind.DANGER)));
            return new Column(resident, Attributes.NONE.id("notice-kinds"));
        }

        /// Adds a resident banner unless it has been dismissed. Its id is its
        /// kind, so the row survives one of its members going.
        private void add(List<Widget> into, Message banner) {
            if (!hidden.contains(banner.kind())) {
                into.add(banner.id("kind-" + banner.kind().cssClass()));
            }
        }

        /// §4's summary, built from a list of failures a real form would have
        /// produced. `orElseThrow` is safe on a literal and is the right shape:
        /// the factory returns an `Optional` because a summary of no errors is no
        /// banner, and a caller that has errors in its hand knows it.
        private Widget summary() {
            return new Column(List.of(
                    Message.summary(List.of(
                            "Name is required",
                            "Port must be between 1024 and 65535")).orElseThrow()),
                    Attributes.NONE.id("notice-summary"));
        }

        /// One button per kind, and one to clear the list.
        private Widget bar() {
            var buttons = new ArrayList<Widget>();
            for (var kind : Message.Kind.values()) {
                var label = kind.name().charAt(0) + kind.name().substring(1).toLowerCase();
                buttons.add(new Button(label, () -> spawn(kind))
                        .withAttributes(Attributes.NONE.id("spawn-" + kind.cssClass())));
            }
            buttons.add(new Spacer());
            // One button for both lists: it takes the spawned banners away and
            // brings the dismissed resident one back, which is the whole of this
            // screen's state in one line.
            buttons.add(new Button("Reset", this::clear)
                    .withAttributes(Attributes.NONE.id("clear-notices").classes("ghost")));
            return new Row(buttons.toArray(Widget[]::new))
                    .withAttributes(Attributes.NONE.id("notice-bar").classes("toolbar"));
        }

        /// What the buttons have spawned, or a line saying nothing has.
        ///
        /// The empty state is a `caption` and not an empty column: a heading over
        /// nothing at all reads as a screen that failed to load.
        private Widget spawnedList() {
            if (notices.isEmpty()) {
                return new Text("Nothing yet.", Attributes.NONE.id("notice-empty")
                        .classes("caption"));
            }
            var banners = new ArrayList<Widget>(notices.size());
            for (var notice : notices) {
                banners.add(new Message(notice.kind(),
                        "Notification " + notice.number() + " — dismiss it and it is gone,"
                                + " because this list is what was describing it.")
                        .dismiss(() -> remove(notice))
                        // The id is the key, so dismissing one from the middle
                        // does not hand its element to the banner below it.
                        .id("notice-" + notice.number()));
            }
            return new Column(banners.toArray(Widget[]::new))
                    .withAttributes(Attributes.NONE.id("notices"));
        }

        private void spawn(Message.Kind kind) {
            setState(() -> notices.add(new Notice(++spawned, kind)));
        }

        private void remove(Notice notice) {
            setState(() -> notices.remove(notice));
        }

        private void clear() {
            setState(() -> {
                notices.clear();
                hidden.clear();
            });
        }

        private void hide(Message.Kind kind) {
            setState(() -> hidden.add(kind));
        }

        /// What the warning banner's action link does.
        ///
        /// Nothing visible, and that is honest: a handler that does not change
        /// what is described does not change what is drawn.
        private void noted() {
            setState(() -> { });
        }
    }
}
