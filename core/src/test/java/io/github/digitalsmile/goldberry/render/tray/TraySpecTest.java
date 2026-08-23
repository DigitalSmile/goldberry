package io.github.digitalsmile.goldberry.render.tray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a tray description will and will not accept.
///
/// These are value rules rather than platform ones, and they are here rather than
/// in the backend's test because both backends inherit them and neither should
/// have to check the same thing twice.
class TraySpecTest {

    @Test
    @DisplayName("refuses a submenu with no rows")
    void anEmptySubmenuOpensNothing() {
        assertThrows(IllegalArgumentException.class, () -> TrayItem.submenu("Recent", List.of()));
    }

    @Test
    @DisplayName("refuses children on a row that is not a submenu")
    void onlySubmenusHaveChildren() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrayItem(TrayItem.Kind.COMMAND, "Open", true, false, null,
                        List.of(TrayItem.command("nested", checked -> {
                        }))));
    }

    @Test
    @DisplayName("lets a separator have no label, and requires one of everything else")
    void separatorsAreTheOnlyUnlabelledRows() {
        assertNull(TrayItem.separator().label());
        assertThrows(NullPointerException.class,
                () -> new TrayItem(TrayItem.Kind.COMMAND, null, true, false, null, List.of()));
    }

    @Test
    @DisplayName("keeps the kind when a row is disabled")
    void disabledIsNotAKind() {
        var row = TrayItem.checkbox("Sounds", true, checked -> {
        }).disabled();

        assertEquals(TrayItem.Kind.CHECKBOX, row.kind());
        assertTrue(row.checked());
        assertFalse(row.enabled());
    }

    @Test
    @DisplayName("does nothing when a row with no handler is chosen")
    void aRowMayDoNothing() {
        // A submenu and a separator both have no handler, and choosing one has
        // to be a no-op rather than a null dereference in whichever backend got
        // there first.
        TrayItem.submenu("Recent", List.of(TrayItem.separator())).choose(false);
        TrayItem.separator().choose(false);
    }

    @Test
    @DisplayName("hands the handler the state it was told")
    void chooseCarriesTheState() {
        var seen = new ArrayList<Boolean>();

        TrayItem.checkbox("Sounds", false, seen::add).choose(true);

        assertEquals(List.of(true), seen);
    }

    @Test
    @DisplayName("treats a blank tooltip as none, because an empty hover box is worse")
    void blankTooltipsBecomeNone() {
        assertNull(TraySpec.of("   ", List.of()).tooltip());
        assertEquals("Goldberry", TraySpec.of("Goldberry", List.of()).tooltip());
    }

    @Test
    @DisplayName("copies the rows it was given, so a caller's list cannot change the menu")
    void rowsAreCopied() {
        var rows = new ArrayList<TrayItem>();
        rows.add(TrayItem.command("Open", checked -> {
        }));

        var spec = TraySpec.of("Goldberry", rows);
        rows.clear();

        assertEquals(1, spec.items().size());
        assertEquals(List.of(), TraySpec.of(null, null).items());
    }

    @Test
    @DisplayName("gives every row, at every depth, something to run after its own handler")
    void andThenReachesEveryRow() {
        // The bug this exists for: a tray row is the only input in the toolkit
        // that arrives with no event behind it, so a handler that sets a model
        // field asks for no frame and changes nothing anybody looks at. Found by
        // running the showcase, where every row except Quit did nothing --
        // because Quit closes a window and the rest only wrote to a model.
        var order = new ArrayList<String>();
        var spec = TraySpec.of("Goldberry", List.of(
                        TrayItem.command("Open", checked -> order.add("Open")),
                        TrayItem.separator(),
                        TrayItem.checkbox("Sounds", false, checked -> order.add("Sounds")),
                        TrayItem.submenu("Recent", List.of(
                                TrayItem.command("report.pdf", checked -> order.add("report.pdf"))))))
                .andThen(() -> order.add("repaint"));

        spec.items().get(0).choose(false);
        spec.items().get(2).choose(true);
        spec.items().get(3).children().get(0).choose(false);

        // After, never before: a repaint asked for first would paint the frame
        // that has not been changed yet.
        assertEquals(
                List.of("Open", "repaint", "Sounds", "repaint", "report.pdf", "repaint"),
                order);
    }

    @Test
    @DisplayName("leaves a submenu and a separator alone, since no platform calls back for either")
    void andThenSkipsWhatIsNeverChosen() {
        var runs = new ArrayList<String>();
        var spec = TraySpec.of("Goldberry", List.of(
                        TrayItem.separator(),
                        TrayItem.submenu("Recent", List.of(TrayItem.separator()))))
                .andThen(() -> runs.add("repaint"));

        spec.items().get(0).choose(false);
        spec.items().get(1).choose(false);

        assertEquals(List.of(), runs);
    }

    @Test
    @DisplayName("still asks for a frame for a row whose own handler is null")
    void andThenCoversAHandlerlessCommand() {
        var runs = new ArrayList<String>();
        var spec = TraySpec.of("Goldberry",
                        List.of(new TrayItem(TrayItem.Kind.COMMAND, "Open", true, false, null,
                                List.of())))
                .andThen(() -> runs.add("repaint"));

        spec.items().get(0).choose(false);

        assertEquals(List.of("repaint"), runs);
    }

    @Test
    @DisplayName("swaps the icon without disturbing the menu, which is what a theme switch does")
    void iconIsReplaceableOnTheSpec() {
        var icon = PixelBuffer.allocate(
                PhysicalSize.of(32, 32), PixelFormat.BGRA32_PREMULTIPLIED);
        var spec = TraySpec.of("Goldberry", List.of(TrayItem.command("Quit", checked -> {
        })));

        var dark = spec.icon(icon);

        assertEquals(icon, dark.icon());
        assertEquals(spec.items(), dark.items());
        assertEquals(spec.tooltip(), dark.tooltip());
    }
}
