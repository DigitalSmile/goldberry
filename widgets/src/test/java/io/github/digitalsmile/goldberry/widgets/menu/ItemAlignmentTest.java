package io.github.digitalsmile.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Every row of a menu puts its label at the same height**, whatever else is on
/// the row — an icon, a tick, a chevron, or nothing.
///
/// The reported symptom was a label one row below an iconed one sitting high, and
/// it is the kind of thing that is invisible in a widget test and obvious on
/// screen: the boxes are all 32 tall either way, and what moves is the text
/// inside them. So this asserts the thing that is actually looked at — where the
/// **paragraph** is painted — across both densities and four display scales,
/// which is every combination the drawing could round differently in.
///
/// A menu built the way [Menus] builds one, because the leading column is decided
/// there and an image of a menu assembled any other way is an image of something
/// nobody sees (ADR-0113).
class ItemAlignmentTest {

    private Icon oversized;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        // 20 rather than 16, which is the size the showcase builds and the case
        // the golden images missed for a month: `item-lead` is 16 square, so an
        // icon larger than it is the one that can push something (ADR-0143).
        oversized = Icon.bundled("palette", 20);
    }

    @AfterEach
    void tearDown() {
        if (oversized != null) {
            oversized.close();
        }
    }

    /// The showcase's own menu, as `Menus` prepares it.
    private Menu showcaseMenu() {
        var menu = new Menu(
                new Item("Switch theme", () -> { }).icon(oversized).accelerator("Ctrl+T"),
                new Item("Switch density", () -> { }).accelerator("Ctrl+D"),
                new Separator(),
                new Item("Frame rate", () -> { }).accelerator("Ctrl+F").checked(true),
                new Item("More").submenu(new Item("Reset", () -> { })));
        var reserve = menu.children().stream()
                .anyMatch(child -> child instanceof Item item
                        && (item.isCheckable() || item.icon() != null));
        return menu.children(menu.children().stream()
                .map(child -> child instanceof Item item
                        ? (Widget) item.reservingLead(reserve)
                        : child)
                .toList());
    }

    /// The same offsets, with the menu squeezed into `width` logical pixels.
    ///
    /// **The reported case.** A popup wider than the window it belongs to is
    /// measured again with the window's width as a definite one (ADR-0104), and
    /// a menu row is a row of measured leaves — so before ADR-0148 the widest row
    /// wrapped to two lines, and a two-line label in a 32px row is centred to the
    /// row's top edge.
    private List<Float> labelOffsetsSqueezedTo(int width) {
        var target = TestFrames.of(width, 240, 1.0f, 0);
        var tree = new ElementTree(showcaseMenu());
        var renderer = new WidgetRenderer(
                Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR), TestFont.get());
        try (var render = RenderTree.create()) {
            // A definite width, which is what the launcher's second measuring
            // pass hands a popup that would not fit.
            render.measure(renderer.render(tree), target.frame().scale(), width, Float.NaN);
            render.update(target.frame(), renderer.render(tree));
            return offsetsOf(render);
        }
    }

    /// Where each row's label was painted, relative to the top of its row.
    ///
    /// The **first text box** in each row, which is the label: the accelerator is
    /// the second, and a test that took whichever came last would be asserting
    /// about `Ctrl+D`.
    private List<Float> labelOffsets(Density density, float scale) {
        var target = TestFrames.of((int) (320 * scale), (int) (240 * scale), scale, 0);
        var tree = new ElementTree(showcaseMenu());
        var renderer = new WidgetRenderer(
                Controls.stylesheets(Theme.NORD_DARK, density), TestFont.get());
        try (var render = RenderTree.create()) {
            render.update(target.frame(), renderer.render(tree));
            return offsetsOf(render);
        }
    }

    private static List<Float> offsetsOf(RenderTree render) {
        var offsets = new ArrayList<Float>();
        var rowTop = new float[] {Float.NaN};
        var seenLabel = new boolean[] {true};
        render.forEachPlacedBox(placed -> {
            var type = placed.box().owner() instanceof Element e ? e.type() : null;
            if ("item".equals(type)) {
                rowTop[0] = placed.layout().top();
                seenLabel[0] = false;
            } else if (placed.box().text() != null && !seenLabel[0]) {
                seenLabel[0] = true;
                offsets.add(placed.layout().top() - rowTop[0]);
            }
        });
        return offsets;
    }

    @Test
    @DisplayName("every label sits at the same height in its row, at every density and scale")
    void labelsAgree() {
        var complaints = new ArrayList<String>();
        for (var density : Density.values()) {
            for (var scale : new float[] {1.0f, 1.25f, 1.5f, 2.0f}) {
                var offsets = labelOffsets(density, scale);
                assertEquals(4, offsets.size(), "four rows, four labels");
                var first = offsets.getFirst();
                for (var i = 1; i < offsets.size(); i++) {
                    // Half a logical pixel, which is what a fractional scale can
                    // legitimately round a box by. A row genuinely aligned to the
                    // top of its own row would be out by eight.
                    if (Math.abs(offsets.get(i) - first) > 0.5f) {
                        complaints.add(String.format(
                                "%s at %.2fx: row %d's label is %.3f from the top of its row"
                                        + " where row 1's is %.3f",
                                density, scale, i + 1, offsets.get(i), first));
                    }
                }
            }
        }
        assertTrue(complaints.isEmpty(), String.join("\n", complaints));
    }

    /// **The reported defect, at the width that produces it.**
    ///
    /// 160 logical pixels is narrower than any row of this menu needs, so every
    /// row is squeezed and the widest ones used to wrap. A label that wrapped
    /// sat at offset 0 — the top of its row — against 8 for the ones that did
    /// not, which is the difference somebody sees and no assertion about the row
    /// would catch (ADR-0148).
    @Test
    @DisplayName("a menu squeezed narrower than its content still puts every label on one line")
    void squeezedRowsDoNotWrap() {
        var offsets = labelOffsetsSqueezedTo(160);

        assertEquals(4, offsets.size());
        for (var i = 0; i < offsets.size(); i++) {
            var index = i;
            assertEquals(offsets.getFirst(), offsets.get(i), 0.001f,
                    () -> "row " + (index + 1) + " wrapped and dropped its label to the top");
        }
        assertTrue(offsets.getFirst() > 1,
                "and they are centred rather than all equally at the top");
    }

    /// The row **after** the iconed one, named on its own because that is the one
    /// that was reported and a failure in the sweep above would not say so.
    @Test
    @DisplayName("the row after an iconed one is aligned like the rest of them")
    void theRowAfterTheIcon() {
        var offsets = labelOffsets(Density.REGULAR, 1.0f);

        assertEquals(offsets.getFirst(), offsets.get(1), 0.001f,
                "the row after the iconed one sits where the iconed one does");
    }
}
