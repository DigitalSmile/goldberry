package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.instance.SingleInstance;
import dev.goldberry.paint.Box;
import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.event.BackendEvent;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// What the system asks an application to open reaches `onOpenUrl`: a URL
/// the backend reports with no window, as SDL does on macOS, and a second
/// launch's command line for a single-instance application.
@DisplayName("opening a URL")
class OpenUrlTest {

    private HeadlessBackend backend;

    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    /// Records what it is asked to open, and stops when it has `expected`.
    private abstract static class Recording implements Application {

        final List<URI> opened = new CopyOnWriteArrayList<>();
        private final int expected;

        Recording(int expected) {
            this.expected = expected;
        }

        @Override
        public Widget root() {
            return new Plate(Attributes.NONE);
        }

        @Override
        public void onOpenUrl(URI target) {
            opened.add(target);
            if (opened.size() == expected) {
                Goldberry.stop();
            }
        }
    }

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(20)
    @DisplayName("a URL the backend reports with no window of its own reaches the application")
    void fromTheBackend() {
        var application = new Recording(1) {
            @Override
            public void start(Host host) {
                backend.post(new BackendEvent.OpenUrl(backend.windows().getFirst(), "tessera://invite/42"));
            }
        };

        Goldberry.launch(application, new String[0]);

        assertEquals(List.of(URI.create("tessera://invite/42")), application.opened);
    }

    @Test
    @Timeout(20)
    @DisplayName("a second launch of a single-instance application hands its URL to the first")
    void fromASecondLaunch() {
        var id = "dev.goldberry.test." + UUID.randomUUID();
        var application = new Recording(1) {
            @Override
            public Optional<String> singleInstance() {
                return Optional.of(id);
            }

            @Override
            public void start(Host host) {
                // What a second process does on its way in: the claim is held,
                // so the arguments go over the socket.
                var second = SingleInstance.acquire(id, List.of("--minimised", "tessera://invite/7"));
                assertTrue(second.isEmpty(), "this launch holds the claim");
            }
        };

        Goldberry.launch(application, new String[0]);

        assertEquals(List.of(URI.create("tessera://invite/7")), application.opened, "the flag is not opened");
        var after = SingleInstance.acquire(id, List.of());
        assertTrue(after.isPresent(), "the claim went with the application");
        after.get().close();
    }
}
