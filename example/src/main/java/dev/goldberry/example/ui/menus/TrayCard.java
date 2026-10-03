package dev.goldberry.example.ui.menus;

import java.util.List;

import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.text.Text;

/// The tray icon the showcase puts up when it starts, and the two commands its
/// menu shares with this window.
///
/// The tray itself is drawn by the desktop's shell, so nothing here draws it: the
/// card says what is in it and offers the same two commands.
///
/// Read more: [The tray icon](https://goldberry.dev/docs/components/menus.html#the-tray-icon).
///
/// @param model   what the two commands change
/// @param actions the commands the tray's first two rows run
record TrayCard(ShowcaseModel model, ShowcaseModel.Actions actions) implements Widget.Stateless {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "menus-tray",
            "The tray icon",
            "The showcase put an icon in the desktop's tray when it started. Its menu is an ordinary menu the"
                    + " shell draws: Switch the light, Switch the density, Screens and Quit. The first two are"
                    + " these buttons.",
            DocLink.to("components/menus", "the-tray-icon"));

    /// Runs `change`, then says so: a plain Java call is not an action a document
    /// dispatched, so a model bound at run time is swept here.
    private void changed(Runnable change) {
        change.run();
        Models.refresh(model);
    }

    @Override
    public Widget build(BuildContext context) {
        return CARD.of(
                new Row(
                        List.of(
                                new Button("Switch the light", () -> changed(actions::toggleTheme)).id("tray-light"),
                                new Button("Switch the density", () -> changed(actions::toggleDensity))
                                        .styled("ghost")
                                        .id("tray-density")),
                        Attributes.NONE.classes("toolbar")),
                new Text(
                        "No icon? This desktop has no notification area, and the showcase runs without one.",
                        Attributes.NONE.classes("caption")));
    }
}
