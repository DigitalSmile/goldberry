package dev.goldberry.example.ui.overlays;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.Placement;
import dev.goldberry.Popup;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.overlay.popover.Popover;
import dev.goldberry.widgets.text.Text;

/// A `popover`, shown in a popup under the button that opened it.
///
/// The two halves are separate on purpose: the popover is the panel, and
/// `Host.popup` measures it, places it against the button, flips it near the
/// bottom of the screen and light-dismisses it.
///
/// Read more: [`popover`](https://goldberry.dev/docs/components/overlays.html#popover).
record PopoverCard() implements Widget.Stateful {

    /// The id the popup is placed against.
    static final String BUTTON = "popover-button";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "overlays-popover",
            "A floating panel",
            "A popover is the panel a popup shows, anchored to a node by id. Press Save: a small panel opens"
                    + " under the button. Press Undo in it, or Escape, or click elsewhere, and it closes.",
            DocLink.to("components/overlays", "popover"));

    @Override
    public State<?> createState() {
        return new PopoverState();
    }

    /// What happened last, and the open popup.
    static final class PopoverState extends State<PopoverCard> {

        private String last = "Not saved yet.";

        /// The window the card is in, read on every build; null where there is
        /// none, as in an offscreen picture.
        private @Nullable Host host;

        private @Nullable Popup open;

        private void save() {
            var window = host;
            if (open != null && open.isOpen()) {
                open.close();
            }
            var popover = new Popover(
                    new Text("Saved a moment ago."),
                    new Button("Undo", this::undo).styled("ghost").id("popover-undo"));
            open = window == null
                    ? null
                    : window.popup(popover, BUTTON, Placement.BELOW).orElse(null);
            setState(() -> last = open == null ? "Saved. This desktop has no popup windows to say so." : "Saved.");
        }

        private void undo() {
            if (open != null) {
                open.close();
                open = null;
            }
            setState(() -> last = "Undone.");
        }

        @Override
        protected void dispose() {
            if (open != null && open.isOpen()) {
                open.close();
            }
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            return CARD.of(
                    new Row(List.of(new Button("Save", this::save).id(BUTTON)), Attributes.NONE.classes("toolbar")),
                    new Text(last, Attributes.NONE.id("popover-last").classes("caption")));
        }
    }
}
