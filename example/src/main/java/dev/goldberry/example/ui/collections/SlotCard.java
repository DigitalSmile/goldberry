package dev.goldberry.example.ui.collections;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Property;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.markup.Bound;
import dev.goldberry.widgets.panel.list.ListView;
import dev.goldberry.widgets.text.Text;

/// A `slot`: a region whose content is whatever widget a value holds, here a
/// detail pane beside a list.
///
/// The region is the widget `slot bind="…"` inflates to, over a property this
/// card owns. Choosing a row replaces the widget in the property, and the region
/// redraws as the new one, which is all a slot does: the list never reaches into
/// the pane.
///
/// Read more: [`slot`](https://goldberry.dev/docs/components/collections.html#slot).
record SlotCard() implements Widget.Stateful {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "collections-slot",
            "A detail pane the model decides",
            "A slot draws whatever widget a bound value holds. Pick one of the Company: the model puts a"
                    + " new widget in the value and the pane on the right redraws as it.",
            DocLink.to("components/collections", "slot"));

    @Override
    public State<?> createState() {
        return new SlotState();
    }

    /// The chosen row, and the value the slot draws.
    static final class SlotState extends State<SlotCard> {

        private @Nullable String chosen;

        /// What the region shows: a message for no selection, a detail for one.
        private final Property<Widget> detail = Property.of(nothingChosen());

        private static Widget nothingChosen() {
            return new Text("Nothing selected", Attributes.NONE.classes("caption"));
        }

        private static Widget detailOf(Walker walker) {
            return new Column(
                    List.of(
                            new Text(
                                    walker.name(),
                                    Attributes.NONE.id("slot-name").classes("body-strong")),
                            new Text(walker.kindred() + " of " + walker.realm(), Attributes.NONE.classes("caption")),
                            new Text(walker.leagues() + " leagues walked", Attributes.NONE.classes("caption"))),
                    Attributes.NONE.classes("slot-detail"));
        }

        private void choose(@Nullable String id) {
            setState(() -> chosen = id);
            detail.set(Walker.COMPANY.stream()
                    .filter(walker -> walker.id().equals(id))
                    .findFirst()
                    .map(SlotState::detailOf)
                    .orElseGet(SlotState::nothingChosen));
        }

        @Override
        public Widget build(BuildContext context) {
            var list = new ListView<>(Walker.COMPANY.subList(0, 5), Walker::id, walker -> new Text(walker.name()))
                    .text(Walker::name)
                    .selected(chosen, this::choose)
                    .withAttributes(Attributes.NONE.id("slot-list"));
            var region = new Bound(detail, Widget.class, Attributes.NONE.id("slot-region"));
            return CARD.of(new Row(List.of(list, region), Attributes.NONE.id("slot-demo")));
        }
    }
}
