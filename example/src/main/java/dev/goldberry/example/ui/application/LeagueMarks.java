package dev.goldberry.example.ui.application;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.bind.Subscription;
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

/// One mark per league walked: a row whose length follows a value, which is a
/// loop, and a loop is something a document cannot write.
///
/// The row is rebuilt when `app.clicks` changes, so this subscribes to the value
/// and asks for a build; a bound `text` would only have changed what one node
/// says, never how many nodes there are.
///
/// Read more: [What markup cannot say](https://goldberry.dev/docs/guide/markup.html#what-markup-cannot-say).
///
/// @param context the application's model and actions
public record LeagueMarks(GalleryContext context) implements Widget.Stateful {

    /// The most marks drawn; the rest are counted.
    static final int MOST = 12;

    @Override
    public State<?> createState() {
        return new MarksState();
    }

    /// The subscription that rebuilds the row.
    static final class MarksState extends State<LeagueMarks> {

        @SuppressWarnings("NullAway.Init") // subscribed in initState()
        private Subscription watching;

        @Override
        protected void initState() {
            watching =
                    Models.observable(widget().context().model(), "app.clicks").subscribe(value -> setState(() -> {}));
        }

        /// The value outlives this widget, so a listener left on it would keep a
        /// dead subtree rebuilding.
        @Override
        protected void dispose() {
            watching.close();
        }

        @Override
        public Widget build(BuildContext context) {
            var app = widget().context();
            var walked = app.model().clicks();
            var marks = new ArrayList<Widget>(MOST + 1);
            for (var league = 1; league <= Math.min(walked, MOST); league++) {
                marks.add(new Badge(String.valueOf(league)).keyed(league));
            }
            if (walked > MOST) {
                marks.add(new Text("and " + (walked - MOST) + " more", Attributes.NONE.classes("caption")));
            }
            if (walked == 0) {
                marks.add(new Text("No leagues yet.", Attributes.NONE.classes("caption")));
            }
            return new Column(
                    List.of(
                            new Row(marks, Attributes.NONE.id("league-marks").classes("league-marks")),
                            new Row(
                                    List.of(
                                            new Button("March a league", app.actions()::click).id("marks-add"),
                                            new Button("Turn back", app.actions()::undo)
                                                    .disabled(walked == 0)
                                                    .id("marks-undo")),
                                    Attributes.NONE.classes("toolbar"))),
                    Attributes.NONE.classes("league-loop"));
        }
    }
}
