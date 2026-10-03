package dev.goldberry.example.ui.windows;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.desktop.notify.Notification;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.text.Text;

/// A desktop notification, sent through the host, with what the desktop
/// answered and how many times the user clicked one.
///
/// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
public record NotifyCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-notify";

    @Override
    public State<?> createState() {
        return new NotifyState();
    }

    static final class NotifyState extends State<NotifyCard> {

        private String answer = "Nothing sent yet.";

        private int sent;

        private int clicked;

        @Override
        public Widget build(BuildContext context) {
            var host = context.host();
            return new ShowcaseCard(
                            ID,
                            "Notifications",
                            "host.notify hands a notification to the desktop: GNOME's banner, Notification Center, a"
                                    + " Windows toast. It answers whether the desktop took it, and false is an"
                                    + " ordinary answer. Click the banner to run its action here.",
                            DocLink.to("guide/windows", "notifications"))
                    .of(
                            new Button("Send a notification", () -> {
                                        var number = sent + 1;
                                        var taken = host.map(window -> window.notify(Notification.of(
                                                                "The Pony is open",
                                                                "Notification " + number + " from the showcase")
                                                        .onActivate(() -> setState(() -> clicked++))))
                                                .orElse(false);
                                        setState(() -> {
                                            sent = number;
                                            answer = taken
                                                    ? "The desktop took notification " + number + "."
                                                    : "The desktop did not take it: no notification service, or"
                                                            + " no window under this host.";
                                        });
                                    })
                                    .id("notify-send")
                                    .styled("primary"),
                            new Text(answer, Attributes.NONE.id("notify-answer").classes("readout")),
                            new Text(
                                    "Clicked: " + clicked,
                                    Attributes.NONE.id("notify-clicked").classes("readout")));
        }
    }
}
