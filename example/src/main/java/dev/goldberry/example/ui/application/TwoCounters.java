package dev.goldberry.example.ui.application;

import java.util.List;

import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.badge.Badge;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// Two counters side by side: one kept in the widget's `State`, one in the
/// application's model.
///
/// They look the same and live differently. The card's own count dies with the
/// card, so leaving the tab and coming back starts it again; `app.clicks` is the
/// application's and outlives every screen that shows it.
///
/// Read more:
/// [Widget state](https://goldberry.dev/docs/applications.html#widget-state-versus-application-state).
///
/// @param context the application's model and actions
public record TwoCounters(GalleryContext context) implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new CountersState();
    }

    /// The card's own count.
    static final class CountersState extends State<TwoCounters> {

        private int local;

        @Override
        public Widget build(BuildContext context) {
            var app = widget().context();
            return new Column(
                    List.of(
                            new Row(
                                    List.of(
                                            new Button("Count here", () -> setState(() -> local++))
                                                    .id("state-local-add"),
                                            new Text("This card:", Attributes.NONE.classes("caption")),
                                            new Badge(String.valueOf(local)).id("state-local")),
                                    Attributes.NONE.classes("toolbar")),
                            new Row(
                                    List.of(
                                            new Button("March a league", app.actions()::click).id("state-app-add"),
                                            new Text("The application:", Attributes.NONE.classes("caption")),
                                            Badge.of("0", Models.observable(app.model(), "app.clicks"))
                                                    .id("state-app")),
                                    Attributes.NONE.classes("toolbar")),
                            new Text(
                                    "Open another tab and come back: the card's count starts again,"
                                            + " the application's does not.",
                                    Attributes.NONE.classes("caption"))),
                    Attributes.NONE.classes("two-counters"));
        }
    }
}
