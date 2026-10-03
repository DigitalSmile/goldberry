package dev.goldberry.example.ui.gallery;

import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.icon.Icon;

/// What a screen is built from: the application's state and actions, the
/// documents, and the few objects only the window has.
///
/// One value handed to every screen, so a screen that needs one more thing does
/// not change the signature of the thirty others.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
///
/// @param model     the state every screen reads
/// @param actions   what every screen's controls ask for
/// @param documents the markup documents, inflated on first use
/// @param plus      the `plus` icon, built once at the size a button draws it
/// @param startTour starts the guided tour, which needs a host a widget does not
///                  have
public record GalleryContext(
        ShowcaseModel model, ShowcaseModel.Actions actions, Documents documents, Icon plus, Runnable startTour) {}
