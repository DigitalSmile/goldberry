package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController;

/// One step of a [Tour] — `docs/core-widgets.md` §5: "each stop names a target by
/// id, a title, a body, and Back/Next/Skip".
///
/// The target is named rather than referenced, for `menu`'s reason
/// ([ADR-0108](../../../../../../../../book/src/adr/0108-a-context-menu-is-a-name-on-a-widget.md)):
/// an application holds ids, not elements, and a tour is usually written far away
/// from the widgets it describes — often in a different file, and often before
/// they exist.
///
/// @param targetId the `id=` of the widget this stop is about
/// @param title    the heading of the popover
/// @param body     what it says
/// @param scroll   the viewport the target lives in, or null when it is not in
///                 one. §5 asks a tour to scroll its target into view, and a
///                 tour cannot find the viewport itself — see [Tour]
public record Stop(String targetId, String title, String body, ScrollController scroll) {

    public Stop {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException(
                    "a tour stop names the widget it describes; without an id it describes nothing");
        }
        title = title == null ? "" : title;
        body = body == null ? "" : body;
    }

    /// A stop whose target is not inside a scroll view.
    public Stop(String targetId, String title, String body) {
        this(targetId, title, body, null);
    }

    /// This stop, told which viewport to scroll to reach its target.
    public Stop within(ScrollController controller) {
        return new Stop(targetId, title, body, controller);
    }
}
