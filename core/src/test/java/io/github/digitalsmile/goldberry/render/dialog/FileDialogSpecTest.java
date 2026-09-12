package io.github.digitalsmile.goldberry.render.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What can be asked for, and the two combinations that cannot.
///
/// The refusals are the point: three kinds rather than one with flags means the
/// impossible requests are impossible to *build*, and the two that survive the
/// type system — a folder dialog with filters, a save dialog for several files —
/// are refused here rather than quietly dropped by whichever platform is under
/// them (ADR-0287).
class FileDialogSpecTest {

    @Test
    @DisplayName("starts empty, and every wither leaves the rest alone")
    void withersKeepTheRest() {
        var spec = FileDialogSpec.openFile()
                .filters(FileFilter.of("Images", "png"))
                .startingAt(Path.of("/tmp"))
                .allowMany(true);

        assertEquals(FileDialogKind.OPEN_FILE, spec.kind());
        assertEquals(List.of(FileFilter.of("Images", "png")), spec.filters());
        assertEquals(Path.of("/tmp"), spec.startingPoint().orElseThrow());
        assertTrue(spec.allowMany());
    }

    @Test
    @DisplayName("has nowhere to start unless told")
    void startsNowhere() {
        assertTrue(FileDialogSpec.saveFile().startingPoint().isEmpty());
        assertFalse(FileDialogSpec.saveFile().allowMany());
        assertEquals(List.of(), FileDialogSpec.openFolder().filters());
    }

    @Test
    @DisplayName("refuses filters on a folder dialog, which has nothing to filter")
    void refusesFiltersOnAFolder() {
        var filters = List.of(FileFilter.of("Images", "png"));

        assertThrows(
                IllegalArgumentException.class,
                () -> FileDialogSpec.openFolder().filters(filters));
    }

    @Test
    @DisplayName("refuses many on a save dialog, which produces one path")
    void refusesManyOnASave() {
        assertThrows(
                IllegalArgumentException.class, () -> FileDialogSpec.saveFile().allowMany(true));
    }

    @Test
    @DisplayName("lets a folder dialog take several, because folders are pickable in bulk")
    void allowsManyFolders() {
        assertTrue(FileDialogSpec.openFolder().allowMany(true).allowMany());
    }

    @Test
    @DisplayName("copies the filter list, so a caller's mutation cannot reach the platform")
    void copiesTheFilters() {
        var filters = new java.util.ArrayList<>(List.of(FileFilter.of("Images", "png")));
        var spec = FileDialogSpec.openFile().filters(filters);

        filters.clear();

        assertEquals(1, spec.filters().size());
    }
}
