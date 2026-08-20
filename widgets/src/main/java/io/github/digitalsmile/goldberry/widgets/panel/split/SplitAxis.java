package io.github.digitalsmile.goldberry.widgets.panel.split;

import java.util.Locale;

/// Which way a [SplitPane]'s two children are laid out.
///
/// **Named after the arrangement, not after the divider**, which is the one thing
/// every split-pane API in the world gets to choose and half of them choose
/// wrongly. Qt's `Qt::Horizontal` means side by side; Java's `JSplitPane`
/// `HORIZONTAL_SPLIT` means the same; GTK's `GtkPaned` says `orientation` and
/// means the arrangement too — but "a vertical split" in conversation almost
/// always means the *divider* is vertical, which is the opposite. So this names
/// the axis the panes travel along and says so in both places somebody might
/// look.
public enum SplitAxis {

    /// Side by side, along the horizontal axis. The divider is a **vertical**
    /// bar and drags left and right. The default, because a split pane is nearly
    /// always a list beside a detail view.
    HORIZONTAL,

    /// Stacked, along the vertical axis. The divider is a **horizontal** bar and
    /// drags up and down.
    VERTICAL;

    /// Whether this axis runs down the screen.
    public boolean isVertical() {
        return this == VERTICAL;
    }

    /// The class a stylesheet selects a pane and its divider by.
    String cssClass() {
        return name().toLowerCase(Locale.ROOT);
    }

    /// Parses the `axis=` attribute.
    static SplitAxis of(String text) {
        if (text == null || text.isBlank()) {
            return HORIZONTAL;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "a split pane's axis is \"horizontal\" (side by side) or \"vertical\""
                            + " (stacked), not \"" + text + "\"", e);
        }
    }
}
