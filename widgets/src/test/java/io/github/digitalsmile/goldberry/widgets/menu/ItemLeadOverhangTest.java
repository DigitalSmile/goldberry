package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// The door an icon's size is chosen at — [ADR-0419].
///
/// An `Icon` is a path built at a size and cannot be rescaled afterwards
/// (ADR-0043), so the only moment anybody can get this right is the call that
/// builds it. `item-lead` is the **one** slot in the catalog with a width of its
/// own — everywhere else `Box.icon` sizes the box to the glyph, so there is no
/// slot to overflow — and 16 was written in `controls.css` and nowhere a Java
/// author would look.
///
/// Two things are held down here. The number, so [Icons#SLOT] and the stylesheet
/// cannot drift apart; and the report, so it stays the *one* line at `debug` that
/// ADR-0394 allows rather than the `WARN` per row per frame it wants to be.
class ItemLeadOverhangTest {

    private Icon fits;
    private Icon oversized;

    @BeforeEach
    void setUp() {
        // Other tests in this module draw the showcase's 20px menu icon, and the
        // set that makes this diagnostic fire once is static by design.
        ItemLead.forgetReportedOverhang();
        fits = Icon.bundled("palette", Icons.SLOT);
        oversized = Icon.bundled("palette", 20);
    }

    @AfterEach
    void tearDown() {
        ItemLead.forgetReportedOverhang();
        if (fits != null) {
            fits.close();
        }
        if (oversized != null) {
            oversized.close();
        }
    }

    /// A one-row menu, prepared the way [Menus] prepares one: the leading column
    /// is reserved by the *menu*, so a row built any other way has no column for
    /// an icon to overflow (ADR-0113).
    private static Box render(Icon icon) {
        var menu = new Menu(new Item("Open", () -> {}).icon(icon));
        var rows = menu.children().stream()
                .map(child -> child instanceof Item item ? (Widget) item.reservingLead(true) : child)
                .toList();
        return new WidgetRenderer(Controls.stylesheets(Theme.NORD_DARK), TestFont.get())
                .render(new ElementTree(menu.children(rows)));
    }

    private static Box find(Box box, String type) {
        if (box.owner() instanceof Element element && type.equals(element.type())) {
            return box;
        }
        for (var child : box.children()) {
            var found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Nested
    @DisplayName("the number, which used to live only in a stylesheet")
    class TheNumber {

        /// The assertion that makes [Icons#SLOT] worth having. If somebody widens
        /// `item-lead` in `controls.css` and leaves the constant at 16, the door
        /// starts telling applications the wrong size — and this is what says so.
        @Test
        @DisplayName("`Icons.SLOT` is the width `controls.css` gives `item-lead`")
        void slotMatchesTheStylesheet() {
            var lead = find(render(fits), "item-lead");

            assertNotNull(lead, "a menu that reserves its lead has an `item-lead`");
            assertEquals(Length.points((float) Icons.SLOT), lead.width());
            assertEquals(Length.points((float) Icons.SLOT), lead.height(), "the column is square");
        }

        /// The shortest correct call. `icons.bind("folder")` is one argument where
        /// `Icon.bundled("folder", 16)` is two, and the one it saves is the one
        /// that was being got wrong.
        @Test
        @DisplayName("`bind(name)` registers a bundled icon at the slot size")
        void bindBuildsAtTheSlotSize() {
            var icons = Icons.strict().bind("folder");

            var registered = icons.resolve("folder");

            assertNotNull(registered);
            assertEquals(Icons.SLOT, registered.size(), 1e-9);
            assertEquals("folder", registered.name());
        }
    }

    @Nested
    @DisplayName("the report, which ADR-0394 says must not be a warning")
    class TheReport {

        /// The common case, and the one that decides whether this diagnostic is
        /// worth having at all: an icon built at the slot size says nothing. A
        /// check that fired here would fire on every menu in the catalog.
        @Test
        @DisplayName("an icon built at the slot size is silent")
        void aFittingIconIsSilent() {
            render(fits);

            assertEquals(List.of(), List.copyOf(ItemLead.reportedOverhang()));
        }

        @Test
        @DisplayName("an icon larger than the column is reported, naming it and both numbers")
        void anOversizedIconIsReported() {
            render(oversized);

            var reported = ItemLead.reportedOverhang();
            assertEquals(1, reported.size());
            var only = reported.iterator().next();
            assertTrue(only.contains("palette"), () -> only + " names the icon");
            assertTrue(only.contains("20"), () -> only + " names the size it was built at");
            assertTrue(only.contains("16"), () -> only + " names the column it did not fit");
        }

        /// **Once**, not once a frame. `render` runs per row per paint, so the
        /// thing that separates this from the log ADR-0243 spent a record
        /// quietening is that the second frame adds nothing.
        @Test
        @DisplayName("the same icon in five frames of five menus is reported once")
        void reportedOnlyOnce() {
            render(oversized);
            render(oversized);
            render(oversized);
            render(oversized);
            render(oversized);

            assertEquals(1, ItemLead.reportedOverhang().size());
        }

        /// Two different mistakes are two facts. The key is the triple and not the
        /// name, so the same glyph at two sizes is two lines — which is what
        /// somebody chasing "why is this one large" needs.
        @Test
        @DisplayName("a second size of the same icon is a second report")
        void sizeIsPartOfTheKey() {
            render(oversized);
            try (var bigger = Icon.bundled("palette", 24)) {
                render(bigger);
            }

            assertEquals(2, ItemLead.reportedOverhang().size());
        }
    }
}
