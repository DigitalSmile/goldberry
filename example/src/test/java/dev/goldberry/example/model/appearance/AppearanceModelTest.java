package dev.goldberry.example.model.appearance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.Theme;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.Scrollbars;

/// The light, the density and the scroll bars, and the one restyle counter
/// every change of them has to reach.
@DisplayName("the appearance slice")
class AppearanceModelTest {

    private final AppearanceModel model = new AppearanceModel();
    private final AppearanceModel.Actions actions = new AppearanceModel.Actions(model);

    @Test
    @DisplayName("opens dark, regular and with overlay scroll bars")
    void defaults() {
        assertEquals(Theme.NORD_DARK, model.theme());
        assertEquals(Density.REGULAR, model.density());
        assertEquals(Scrollbars.OVERLAY, model.scrollbars());
    }

    @Test
    @DisplayName("the light switches back and forth, by toggle and by the switch's state")
    void theLight() {
        actions.toggleTheme();
        assertEquals(Theme.NORD_LIGHT, model.theme());
        assertEquals("light", Models.observable(model, "app.theme").get());

        actions.setLight(false);
        assertEquals(Theme.NORD_DARK, model.theme());
        assertEquals("dark", Models.observable(model, "app.theme").get());
    }

    @Test
    @DisplayName("density and scroll bars each flip between their two settings")
    void theTokens() {
        actions.toggleDensity();
        actions.toggleScrollbars();
        assertEquals(Density.COMPACT, model.density());
        assertEquals(Scrollbars.ALWAYS, model.scrollbars());

        actions.toggleDensity();
        actions.toggleScrollbars();
        assertEquals(Density.REGULAR, model.density());
        assertEquals(Scrollbars.OVERLAY, model.scrollbars());
    }

    @Test
    @DisplayName("every one of its values asks for a restyle, because every one is a stylesheet")
    void everyChangeRestyles() {
        var restyles = new AtomicInteger();
        List<Runnable> changes = List.of(actions::toggleTheme, actions::toggleDensity, actions::toggleScrollbars);
        try (var _ = Models.onRestyle(model, restyles::incrementAndGet)) {
            for (var change : changes) {
                var before = restyles.get();
                change.run();
                // A woven model has already said so; a model bound at run time
                // says so when it is swept, as the window sweeps it every frame.
                Models.refresh(model);
                assertTrue(restyles.get() > before, "a change to how the window is drawn did not restyle it");
            }
        }
    }
}
