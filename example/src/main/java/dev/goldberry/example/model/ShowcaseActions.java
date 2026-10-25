package dev.goldberry.example.model;

import java.util.List;
import java.util.Objects;

import dev.goldberry.example.model.appearance.AppearanceModel;
import dev.goldberry.example.model.controls.ControlsModel;
import dev.goldberry.example.model.documents.DocumentsModel;
import dev.goldberry.example.model.forms.FormsModel;
import dev.goldberry.example.model.navigation.NavigationModel;
import dev.goldberry.example.model.pickers.PickersModel;
import dev.goldberry.example.model.sheets.SheetsModel;
import dev.goldberry.example.model.shell.ShellModel;

/// Everything that *happens* to the showcase's values, as one value: each
/// slice's actions, over that slice.
///
/// The other half of [ShowcaseModel], and shaped like it: every slice's
/// `Actions` record is nested in the slice it writes, so the slice's fields stay
/// `private`, and this record only holds them together for Java to pass around.
///
/// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
///
/// @param shell      moving between screens, the reading size and the bar
/// @param appearance the light, the density and the scroll bars
/// @param controls   the road, the choices, the gain, the chips and the tags
/// @param forms      the Forms screen's fields
/// @param pickers    the date, the time and the colour
/// @param documents  editing and following the two documents
/// @param navigation the chapters in the strip and the breadcrumbs
/// @param sheets     the Icons and Emoji screens' queries
public record ShowcaseActions(
        ShellModel.Actions shell,
        AppearanceModel.Actions appearance,
        ControlsModel.Actions controls,
        FormsModel.Actions forms,
        PickersModel.Actions pickers,
        DocumentsModel.Actions documents,
        NavigationModel.Actions navigation,
        SheetsModel.Actions sheets) {

    public ShowcaseActions {
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(controls, "controls");
        Objects.requireNonNull(forms, "forms");
        Objects.requireNonNull(pickers, "pickers");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(navigation, "navigation");
        Objects.requireNonNull(sheets, "sheets");
    }

    /// The actions over every slice of `model`.
    public ShowcaseActions(ShowcaseModel model) {
        this(
                new ShellModel.Actions(model.shell()),
                new AppearanceModel.Actions(model.appearance()),
                new ControlsModel.Actions(model.controls()),
                new FormsModel.Actions(model.forms()),
                new PickersModel.Actions(model.pickers()),
                new DocumentsModel.Actions(model.documents()),
                new NavigationModel.Actions(model.navigation()),
                new SheetsModel.Actions(model.sheets()));
    }

    /// Every slice's `@Actions` record, in [ShowcaseModel#slices()]'s order.
    public List<Object> slices() {
        return List.of(shell, appearance, controls, forms, pickers, documents, navigation, sheets);
    }

    /// Whether every one of these writes a slice of `model` — and not a slice of
    /// some other showcase, which would be a window whose documents read one set
    /// of values while its controls change another.
    public boolean write(ShowcaseModel model) {
        return shell.values() == model.shell()
                && appearance.values() == model.appearance()
                && controls.values() == model.controls()
                && forms.values() == model.forms()
                && pickers.values() == model.pickers()
                && documents.values() == model.documents()
                && navigation.values() == model.navigation()
                && sheets.values() == model.sheets();
    }
}
