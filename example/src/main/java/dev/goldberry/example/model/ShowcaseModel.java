package dev.goldberry.example.model;

import java.util.List;
import java.util.Objects;

import dev.goldberry.example.media.ShowcasePlayers;
import dev.goldberry.example.model.appearance.AppearanceModel;
import dev.goldberry.example.model.controls.ControlsModel;
import dev.goldberry.example.model.documents.DocumentsModel;
import dev.goldberry.example.model.forms.FormsModel;
import dev.goldberry.example.model.navigation.NavigationModel;
import dev.goldberry.example.model.pickers.PickersModel;
import dev.goldberry.example.model.sheets.SheetsModel;
import dev.goldberry.example.model.shell.ShellModel;
import dev.goldberry.widgets.markup.Named;

/// Everything the showcase *knows*, as one value: a slice per part of the window,
/// and the players.
///
/// Not a model itself. Each slice is its own `@Model`, publishing its own paths,
/// and the window hands every one of them to the toolkit — see
/// [#slices()]. This record is what Java passes around, so a screen that needs one
/// more slice does not change thirty signatures.
///
/// ```java
/// var model = new ShowcaseModel();
/// var actions = new ShowcaseActions(model);
/// model.controls().clicks(); // 0
/// actions.controls().click();
/// ```
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
///
/// @param shell      the screen showing, the history, the reading size and the
///                   bar's readings
/// @param appearance the light, the density and the scroll bars
/// @param controls   the road, the choices, the gain, the chips and the tags
/// @param forms      the Forms screen's fields and the objects its document names
/// @param pickers    the date, the time and the colour
/// @param documents  the Markdown and HTML screens' sources
/// @param navigation the chapters in the strip and the breadcrumbs' path
/// @param sheets     the Icons and Emoji screens' queries
/// @param media      the Audio and Video screens' players
public record ShowcaseModel(
        ShellModel shell,
        AppearanceModel appearance,
        ControlsModel controls,
        FormsModel forms,
        PickersModel pickers,
        DocumentsModel documents,
        NavigationModel navigation,
        SheetsModel sheets,
        ShowcasePlayers media) {

    public ShowcaseModel {
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(controls, "controls");
        Objects.requireNonNull(forms, "forms");
        Objects.requireNonNull(pickers, "pickers");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(navigation, "navigation");
        Objects.requireNonNull(sheets, "sheets");
        Objects.requireNonNull(media, "media");
    }

    /// A showcase that has done nothing yet: every slice at its starting value,
    /// and players that have opened nothing.
    public ShowcaseModel() {
        this(
                new ShellModel(),
                new AppearanceModel(),
                new ControlsModel(),
                new FormsModel(),
                new PickersModel(),
                new DocumentsModel(),
                new NavigationModel(),
                new SheetsModel(),
                ShowcasePlayers.create());
    }

    /// Every `@Model` slice, in the order the window lists them. The players are
    /// not among them: nothing about a player is bound.
    public List<Object> slices() {
        return List.of(shell, appearance, controls, forms, pickers, documents, navigation, sheets);
    }

    /// What the documents may name — see [dev.goldberry.widgets.markup.Named]:
    /// the Forms document's controller and rule, the pictures both document
    /// screens show, and the media screens' players.
    ///
    /// Strict, so a document naming an object nobody registered is refused rather
    /// than inflated around a hole.
    public Named named() {
        return media.register(documents.register(forms.register(Named.strict())));
    }
}
