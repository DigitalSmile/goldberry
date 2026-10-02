package dev.goldberry.widgets.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.key.Shortcut;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.TestHost;

/// What [Accelerators#unbind] gives back, with an owner and without one.
///
/// The two-argument form is documented as "whoever holds those keys now". It
/// passed a null owner down to the host, which a host reads as "only the keys
/// nobody owns", so a key a `menubar` had bound for itself survived it.
@DisplayName("Accelerators.unbind")
class AcceleratorsUnbindTest {

    private static final Shortcut OPEN = Shortcut.of("Ctrl+O");

    private static List<Widget> fileMenu() {
        return List.of(new Item("File").submenu(new Item("Open…", () -> {}).accelerator("Ctrl+O")));
    }

    @Test
    @DisplayName("without an owner, takes the key whoever holds it")
    void withoutAnOwnerTakesTheKey() {
        var host = new TestHost();
        var bound = Accelerators.bind(host, fileMenu(), new Object());
        assertEquals(Set.of(OPEN), bound);

        Accelerators.unbind(host, bound);

        assertTrue(
                host.shortcuts().isEmpty(),
                "the owner-less unbind left " + host.shortcuts().keySet());
    }

    @Test
    @DisplayName("with an owner, takes only what that owner still holds")
    void withAnOwnerTakesOnlyItsOwn() {
        var host = new TestHost();
        var bar = new Object();
        var bound = Accelerators.bind(host, fileMenu(), bar);
        // The application binds the same key afterwards, and the bar is unmounted.
        host.shortcut(OPEN, () -> {}, new Object());

        Accelerators.unbind(host, bound, bar);

        assertEquals(Set.of(OPEN), host.shortcuts().keySet(), "the bar took the application's key with it");
    }
}
