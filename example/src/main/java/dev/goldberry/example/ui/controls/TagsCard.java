package dev.goldberry.example.ui.controls;

import java.util.ArrayList;

import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.icon.Icon;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.chip.Chip;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// Chips a user can take off.
///
/// A × removes a chip, so the row is a different tree after a dismiss: the chip
/// asks, the model drops the tag, and the row is built again from what the model
/// holds. That is why the card follows `app.tags` and builds one chip per tag.
///
/// Read more: [`chip`](https://goldberry.dev/docs/components/buttons.html#chip).
///
/// @param model   where the tags are kept
/// @param actions what a × and Put them back ask for
public record TagsCard(ShowcaseModel model, ShowcaseModel.Actions actions) implements Widget.Stateful {

    /// The leading icon on every tag. An icon is an immutable value, so one is
    /// built for the class and shared by every chip.
    private static final Icon TAG = Icon.bundled("tag", Icons.SLOT);

    @Override
    public State<?> createState() {
        return new TagsState();
    }

    /// The subscription to the tags.
    static final class TagsState extends State<TagsCard> {

        @SuppressWarnings("NullAway.Init") // subscribed in initState()
        private Subscription watching;

        @Override
        protected void initState() {
            watching = Models.observable(widget().model(), "app.tags").subscribe(_ -> setState(() -> {}));
        }

        @Override
        protected void dispose() {
            watching.close();
        }

        @Override
        public Widget build(BuildContext context) {
            var actions = widget().actions();
            var model = widget().model();
            var tags = model.tags();
            var chips = new ArrayList<Widget>(tags.size() + 1);
            for (var tag : tags) {
                // Keyed by the tag, so removing one from the middle reconciles the
                // rest in place rather than shuffling every chip's state along one.
                chips.add(new Chip(tag)
                        .withIcon(TAG)
                        .onDismiss(Swept.after(model, () -> actions.dropTag(tag)))
                        .keyed(tag)
                        .id("tag-" + tag));
            }
            if (tags.isEmpty()) {
                chips.add(new Text("Nothing left to take off.", Attributes.NONE.classes("caption")));
            }
            return new ShowcaseCard(
                            "tag-card",
                            "Chips you can take off",
                            "A chip with a dismiss action draws a ×. It removes nothing itself: it asks, and the"
                                    + " list it shows is the application's. Click a ×, or focus a chip and press"
                                    + " Delete or Backspace.",
                            DocLink.to("components/buttons", "chip"))
                    .of(
                            new Row(chips, Attributes.NONE.id("chip-tags")),
                            new Button("Put them back", Swept.after(model, actions::resetTags)).id("reset-tags"));
        }
    }
}
