package io.github.digitalsmile.goldberry.render.dialog;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/// What to ask the user for: which dialog, what to filter, where to start, and
/// whether one answer is enough.
///
/// ```java
/// host.fileDialog(
///         FileDialogSpec.saveFile()
///                 .filters(FileFilter.of("PNG image", "png"))
///                 .startingAt(Path.of(System.getProperty("user.home"), "board.png")),
///         choice -> switch (choice) {
///             case FileChoice.Chosen(var paths, var filter) -> export(paths.getFirst());
///             case FileChoice.Cancelled ignored -> { }
///             case FileChoice.Failed(var message) -> toast(message);
///         });
/// ```
///
/// **Every field but the kind is a hint.** Not all platforms honour a starting
/// directory, several let the user switch filtering off, and a few ignore
/// multi-select. That is why nothing here is validated against what came back:
/// the answer is checked, not the request.
///
/// **There is no title, accept label or cancel label**, and their absence is a
/// decision rather than an omission. SDL exposes them only through
/// `SDL_ShowFileDialogWithProperties`, which means a second code path and a
/// properties object for three strings that the platform's own dialogs are
/// entitled to ignore — macOS has no dialog title at all. When something needs
/// them, they arrive together with that call and a record component each
/// (ADR-0287).
///
/// @param kind      which dialog
/// @param filters   the type dropdown, in order; empty for none
/// @param location  the file or folder to start at, or null for the platform's
///                  own idea of where the user was last
/// @param allowMany whether more than one entry may be chosen
public record FileDialogSpec(
        FileDialogKind kind,
        List<FileFilter> filters,
        @Nullable Path location,
        boolean allowMany) {

    public FileDialogSpec {
        Objects.requireNonNull(kind, "kind");
        filters = List.copyOf(filters == null ? List.of() : filters);
        if (!filters.isEmpty() && !kind.takesFilters()) {
            throw new IllegalArgumentException(
                    kind + " has nothing to filter; drop the filters or open a file instead");
        }
        if (allowMany && !kind.takesMany()) {
            throw new IllegalArgumentException(kind + " produces one path; a save dialog cannot name several files");
        }
    }

    /// Pick one or more existing files.
    public static FileDialogSpec openFile() {
        return new FileDialogSpec(FileDialogKind.OPEN_FILE, List.of(), null, false);
    }

    /// Name a file to write.
    public static FileDialogSpec saveFile() {
        return new FileDialogSpec(FileDialogKind.SAVE_FILE, List.of(), null, false);
    }

    /// Pick a directory.
    public static FileDialogSpec openFolder() {
        return new FileDialogSpec(FileDialogKind.OPEN_FOLDER, List.of(), null, false);
    }

    /// The same request with this type dropdown.
    public FileDialogSpec filters(FileFilter... values) {
        return filters(List.of(values));
    }

    /// The same request with this type dropdown.
    public FileDialogSpec filters(List<FileFilter> values) {
        return new FileDialogSpec(kind, values, location, allowMany);
    }

    /// The same request, starting at a file or a folder.
    public FileDialogSpec startingAt(Path value) {
        return new FileDialogSpec(kind, filters, value, allowMany);
    }

    /// The same request, letting the user choose several.
    public FileDialogSpec allowMany(boolean value) {
        return new FileDialogSpec(kind, filters, location, value);
    }

    /// Where to start, if anywhere.
    public Optional<Path> startingPoint() {
        return Optional.ofNullable(location);
    }
}
