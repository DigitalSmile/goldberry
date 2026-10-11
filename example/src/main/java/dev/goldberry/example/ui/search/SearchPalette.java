package dev.goldberry.example.ui.search;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.Overlay;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.overlay.dialog.Dialog;
import dev.goldberry.widgets.overlay.dialog.Dialogs;

/// Puts the [CommandPalette] on a window, and remembers what was picked from it.
///
/// The half of the palette that is not a widget, for the reason `Dialogs` is
/// apart from `Dialog`: a modal needs the window, and a widget has none. One per
/// window. Opening it while it is open puts the keyboard back in its field
/// rather than stacking a second one.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#dialog).
public final class SearchPalette {

    /// The dialog's id.
    public static final String ID = "search";

    private final RecentPicks recent = new RecentPicks();

    private @Nullable Overlay open;

    /// Opens the palette over `host`'s window.
    ///
    /// @param entries what there is to find
    /// @param go      what choosing a result does, after the palette has
    ///                started to close
    public void open(Host host, List<SearchEntry> entries, Consumer<SearchEntry> go) {
        if (isOpen()) {
            host.focus(CommandPalette.INPUT, true);
            return;
        }
        var palette = new CommandPalette(entries, recent.keys(), entry -> {
            recent.remember(entry.key());
            close();
            go.accept(entry);
        });
        open = Dialogs.show(host, new Dialog(null, List.of(palette), this::closed, Attributes.NONE.id(ID)));
    }

    /// Whether the palette is on the window.
    public boolean isOpen() {
        return open != null && open.isAttached();
    }

    /// Takes the palette away, with the fade every dialog leaves with.
    public void close() {
        var showing = open;
        open = null;
        if (showing != null) {
            showing.dismiss();
        }
    }

    /// The keys picked lately, most recent first.
    public List<String> recent() {
        return recent.keys();
    }

    private void closed() {
        open = null;
    }
}
