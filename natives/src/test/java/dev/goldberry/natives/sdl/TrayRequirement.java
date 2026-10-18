package dev.goldberry.natives.sdl;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import dev.goldberry.natives.sdl.desktop.SdlTray;
import dev.goldberry.natives.sdl.desktop.SdlTrayItem;

/// What a test that needs a real tray does where there is none.
///
/// Most machines that run the suite have no notification area: CI's Linux lane
/// runs under a virtual X server with no shell, and a container has no desktop
/// at all, so by default these tests skip. The Windows and macOS runners do
/// have one, and `-Dgoldberry.tray.required=true` is how their jobs say so, the
/// way `goldberry.webview.required` does for the web view.
///
/// The decision is [#decide], a pure function of its inputs, so it is tested
/// directly rather than by contorting system properties.
public sealed interface TrayRequirement {

    /// Set by the test tasks from `-Dgoldberry.tray.required` or
    /// `-Pgoldberry.tray.required`; see `ForwardedProperties`.
    String REQUIRED_PROPERTY = "goldberry.tray.required";

    /// There is a tray. Run the test.
    record Run() implements TrayRequirement {}

    /// None, and nobody promised one. Abort with a reason.
    ///
    /// @param reason why the test did not run
    record Skip(String reason) implements TrayRequirement {}

    /// None where one was required. Fail.
    ///
    /// @param reason what was missing
    record Fail(String reason) implements TrayRequirement {}

    /// Decides from whether a tray opened, why not if it did not, and whether
    /// the run declared one mandatory.
    ///
    /// @param opened   whether SDL put an icon up
    /// @param required whether this run promised a notification area
    /// @param why      what stood in the way, SDL's words or the video
    ///                 subsystem's; may be null
    static TrayRequirement decide(boolean opened, boolean required, @Nullable String why) {
        if (opened) {
            return new Run();
        }
        var reason = why == null || why.isBlank() ? "this desktop has no system tray" : why;
        return required
                ? new Fail("a tray is required for this run but none opened: " + reason
                        + ". The tray's platform code would ship unexercised.")
                : new Skip(reason);
    }

    /// Starts SDL's video subsystem and opens a tray with `items`, or aborts
    /// or fails the test as [#decide] says.
    static SdlTray open(List<SdlTrayItem> items) {
        var required = Boolean.getBoolean(REQUIRED_PROPERTY);
        Optional<SdlTray> tray = Optional.empty();
        String why = null;
        try {
            Sdl.get().initialize(Set.of(SdlSubsystem.VIDEO));
            tray = SdlTray.open(null, "Goldberry", items);
            if (tray.isEmpty()) {
                why = Sdl.get().lastError();
            }
        } catch (SdlException e) {
            why = "SDL has no video subsystem here: " + e.getMessage();
        }
        switch (decide(tray.isPresent(), required, why)) {
            case Run _ -> {}
            case Skip(var reason) -> Assumptions.abort(reason);
            case Fail(var reason) -> Assertions.fail(reason);
        }
        return tray.orElseThrow();
    }
}
