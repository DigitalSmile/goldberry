package dev.goldberry.example.ui.menus;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.MenuBar;
import dev.goldberry.widgets.menu.Separator;
import dev.goldberry.widgets.text.Text;

/// Accelerators: a menu bar whose rows name keys, which work with every menu
/// shut because the bar binds them while it is mounted.
///
/// `Primary` is the desktop's own modifier, `Cmd` on macOS and `Ctrl` elsewhere,
/// so the same text fits both.
///
/// Read more: [Accelerators](https://goldberry.dev/docs/components/menus.html#accelerators).
record AcceleratorsCard() implements Widget.Stateful {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "menus-accelerators",
            "Keys that work with the menu shut",
            "A menu bar binds every accelerator its rows name while it is on screen. With no menu open, press"
                    + " Ctrl+Shift+A to add a league and Ctrl+Shift+X to take one back. Primary means Cmd on a"
                    + " Mac and Ctrl elsewhere.",
            DocLink.to("components/menus", "accelerators"));

    @Override
    public State<?> createState() {
        return new AcceleratorsState();
    }

    /// The tally the keys change.
    static final class AcceleratorsState extends State<AcceleratorsCard> {

        private int leagues;

        private void add() {
            setState(() -> leagues++);
        }

        private void takeBack() {
            setState(() -> leagues = Math.max(0, leagues - 1));
        }

        private void startOver() {
            setState(() -> leagues = 0);
        }

        @Override
        public Widget build(BuildContext context) {
            var bar = new MenuBar(new Item("Tally")
                            .submenu(
                                    new Item("Add a league", this::add).accelerator("Primary+Shift+A"),
                                    new Item("Take one back", this::takeBack).accelerator("Primary+Shift+X"),
                                    new Separator(),
                                    new Item("Start over", this::startOver).accelerator("Primary+Shift+N")))
                    .id("tally-menubar");
            return CARD.of(
                    bar,
                    new Text(
                            leagues == 1 ? "1 league" : leagues + " leagues",
                            Attributes.NONE.id("tally").classes("body-strong")));
        }
    }
}
