package dev.goldberry.example.ui.gallery;

import java.util.Set;

import dev.goldberry.Goldberry;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.icon.Icon;
import dev.goldberry.platform.Capability;

/// What a screen is built from: the application's state and actions, the
/// documents, and the few objects only the window has.
///
/// One value handed to every screen, so a screen that needs one more thing does
/// not change the signature of the thirty others.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
///
/// @param model        the state every screen reads
/// @param actions      what every screen's controls ask for
/// @param documents    the markup documents, inflated on first use
/// @param plus         the `plus` icon, built once at the size a button draws it
/// @param startTour    starts the guided tour, which needs a host a widget does not
///                     have
/// @param capabilities what the screens say this build can do: the window's is
///                     `Goldberry.capabilities()`, and a picture of a screen
///                     pins it, because the build machine is not something a
///                     golden may photograph
public record GalleryContext(
        ShowcaseModel model,
        ShowcaseModel.Actions actions,
        Documents documents,
        Icon plus,
        Runnable startTour,
        Set<Capability> capabilities) {

    public GalleryContext {
        capabilities = Set.copyOf(capabilities);
    }

    /// A context that reports what the loaded library answers.
    public GalleryContext(
            ShowcaseModel model, ShowcaseModel.Actions actions, Documents documents, Icon plus, Runnable startTour) {
        this(model, actions, documents, plus, startTour, Goldberry.capabilities());
    }
}
