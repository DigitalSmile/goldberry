package dev.goldberry.example.ui.menus;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.Popup;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.Menu;
import dev.goldberry.widgets.menu.Menus;
import dev.goldberry.widgets.menu.Separator;
import dev.goldberry.widgets.text.Text;

/// A `menu` of this card's own, opened with `Menus.open` from the host the card
/// is built in: every kind of row, and a line that says which one ran.
///
/// Read more: [`menu`](https://goldberry.dev/docs/components/menus.html#menu).
record RowMenuCard() implements Widget.Stateful {

    /// The id the menu is placed against.
    static final String BUTTON = "row-menu-button";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "menus-row-menu",
            "A menu of your own",
            "A menu holds items, separators and submenus, and Menus.open puts it under a node by id. Choosing"
                    + " a row runs it and closes the whole stack. Rest on Move to for its submenu.",
            DocLink.to("components/menus", "menu"));

    @Override
    public State<?> createState() {
        return new RowMenuState();
    }

    /// What the last row did, whether the row is pinned, and the open menu.
    static final class RowMenuState extends State<RowMenuCard> {

        private String last = "Nothing chosen yet.";

        private boolean pinned;

        /// The window the card is in, read on every build; null where there is
        /// none, as in an offscreen picture.
        private @Nullable Host host;

        private @Nullable Popup open;

        private void did(String what) {
            setState(() -> last = what);
        }

        private void togglePin() {
            setState(() -> {
                pinned = !pinned;
                last = pinned ? "Pinned." : "Unpinned.";
            });
        }

        private Menu menu() {
            return new Menu(
                    new Item("Rename", () -> did("Renamed.")).accelerator("F2"),
                    new Item("Duplicate", () -> did("Duplicated.")),
                    new Item("Pinned", this::togglePin).checked(pinned),
                    new Item("Move to")
                            .submenu(
                                    new Item("Archive", () -> did("Moved to the archive.")),
                                    new Item("Rivendell", () -> did("Moved to Rivendell."))),
                    new Separator(),
                    new Item("Delete", () -> did("Deleted.")),
                    new Item("Nothing to undo").disabled(true));
        }

        private void toggle() {
            if (open != null && open.isOpen()) {
                open.close();
                open = null;
                return;
            }
            var window = host;
            open = window == null ? null : Menus.open(window, BUTTON, menu()).orElse(null);
            if (open == null) {
                did("No popup windows here, so the menu cannot open.");
            }
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
                    new Row(
                            List.of(new Button("Row actions", this::toggle).id(BUTTON)),
                            Attributes.NONE.classes("toolbar")),
                    new Text(last, Attributes.NONE.id("row-menu-last").classes("caption")));
        }
    }
}
