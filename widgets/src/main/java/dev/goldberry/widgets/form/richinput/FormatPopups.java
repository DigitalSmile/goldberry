package dev.goldberry.widgets.form.richinput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.OpenedFrom;
import dev.goldberry.Placement;
import dev.goldberry.Popup;
import dev.goldberry.icon.Icon;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.edit.keys.FormatCommand;
import dev.goldberry.widget.BuildContext;

/// The two panels a `rich-input` opens over its selection: the formatting bar,
/// which never takes the keyboard, and the link editor, which does.
///
/// Holds the bar's icons too. A button borrows its icon, so whoever builds the
/// bar owns them; they are made the first time a bar needs them and closed
/// with the field.
final class FormatPopups implements AutoCloseable {

    /// The size the bar's icons are drawn at, in logical pixels.
    static final double ICON_SIZE = 16;

    private final Map<String, Icon> icons = new HashMap<>();

    private @Nullable Popup bar;

    /// Where the open bar was placed, so a bar whose selection moved is put
    /// where it now is.
    private @Nullable LogicalRect barAnchor;

    private @Nullable Popup link;

    /// Shows the bar over `anchor`, or brings an open one up to date.
    void showBar(
            Host host,
            BuildContext opener,
            LogicalRect anchor,
            List<FormatButton> buttons,
            Set<FormatCommand> active,
            Consumer<FormatCommand> onPress) {
        var content = new FormatBar(buttons, active, iconsFor(buttons), onPress);
        var open = bar;
        if (open != null && open.isOpen() && anchor.equals(barAnchor)) {
            open.content(content);
            return;
        }
        hideBar();
        barAnchor = anchor;
        OpenedFrom.call(
                        opener,
                        () -> host.attachedPopup(content, anchor, Placement.ABOVE, 0, (widget, size, area) -> widget))
                .ifPresent(popup -> bar = popup.takesFocus(false));
    }

    /// Puts the bar away, if it is out.
    void hideBar() {
        var open = bar;
        bar = null;
        barAnchor = null;
        if (open != null && open.isOpen()) {
            open.close();
        }
    }

    /// Whether the bar is out.
    boolean barShown() {
        return bar != null && bar.isOpen();
    }

    /// Opens the link editor under `anchor`, with the keyboard in its address.
    void showLink(Host host, BuildContext opener, LogicalRect anchor, LinkEditor editor) {
        hideBar();
        hideLink();
        Optional<Popup> opened = OpenedFrom.call(opener, () -> host.popup(editor, anchor, Placement.BELOW));
        opened.ifPresent(popup -> {
            popup.focusOn(LinkEditor.ADDRESS);
            link = popup.lightDismiss(true);
        });
    }

    /// Puts the link editor away, if it is out.
    void hideLink() {
        var open = link;
        link = null;
        if (open != null && open.isOpen()) {
            open.close();
        }
    }

    /// Whether the link editor is out.
    boolean linkShown() {
        return link != null && link.isOpen();
    }

    private Map<String, Icon> iconsFor(List<FormatButton> buttons) {
        for (var button : buttons) {
            icons.computeIfAbsent(button.icon(), name -> Icon.bundled(name, ICON_SIZE));
        }
        return Map.copyOf(icons);
    }

    /// Both panels away and the icons closed.
    @Override
    public void close() {
        hideBar();
        hideLink();
        for (var icon : icons.values()) {
            icon.close();
        }
        icons.clear();
    }

    /// For a test: the icons made so far, by name.
    Map<String, Icon> icons() {
        return Map.copyOf(icons);
    }
}
