package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.junit.HeadlessRuntime;
import dev.goldberry.widget.Widget;

@ExtendWith(HeadlessRuntime.class)
class LauncherStylesheetsTest {

    @Test
    void readsTheStylesheetsOnceForTheFirstRenderer() {
        RendererRequirement.enforce();
        var reads = new AtomicInteger();
        var app = new Application() {
            @Override
            public Widget root() {
                return Widget.nothing();
            }

            @Override
            public List<Stylesheet> stylesheets() {
                reads.incrementAndGet();
                return List.of();
            }
        };

        Goldberry.launch(app, new String[] {"--frames=1", "--size=80x60"});

        assertEquals(1, reads.get());
    }
}
