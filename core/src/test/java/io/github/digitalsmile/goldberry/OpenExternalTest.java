package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// [Host#openExternal] — a URL handed to the desktop through the backend
/// ([ADR-0346]).
///
/// Headless, so there is no desktop: the backend records what it was asked
/// and answers true. The SDL half is `SdlOpenUrlTest` in `:natives`.
class OpenExternalTest {

    private record Plate(Attributes attributes) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "plate";
        }

        @Override
        public Set<String> classes() {
            return attributes.classes();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).grow(1);
        }
    }

    private static final class OpeningApp implements Application {

        boolean answered;

        @Override
        public Widget root() {
            return new Plate(Attributes.NONE);
        }

        @Override
        public LogicalSize size() {
            return LogicalSize.of(200, 100);
        }

        @Override
        public List<Stylesheet> stylesheets() {
            return List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "plate { background: #204060 }"));
        }

        @Override
        public void start(Host host) {
            answered = host.openExternal("https://example.org/docs");
            host.window().close();
        }
    }

    private HeadlessBackend backend;

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
    @Timeout(10)
    @DisplayName("the host hands the URL to the backend and reports its answer")
    void handedToTheBackend() {
        var app = new OpeningApp();

        Goldberry.launch(app);

        assertEquals(List.of("https://example.org/docs"), backend.openedUrls());
        assertTrue(app.answered);
    }
}
