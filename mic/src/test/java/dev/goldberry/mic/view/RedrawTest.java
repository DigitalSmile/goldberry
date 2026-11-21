package dev.goldberry.mic.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a widget's redraw")
class RedrawTest {

    @Test
    @DisplayName("is handed to the UI thread once however many blocks arrive before it runs, and again after")
    void coalesces() {
        var queued = new ArrayList<Runnable>();
        var refreshed = new AtomicInteger();
        var redraw = Redraw.coalesced(new AtomicBoolean(), queued::add, refreshed::incrementAndGet);

        redraw.run();
        redraw.run();
        redraw.run();
        assertEquals(1, queued.size(), "three blocks, one redraw");

        List.copyOf(queued).forEach(Runnable::run);
        assertEquals(1, refreshed.get());

        redraw.run();
        assertEquals(2, queued.size(), "a block after the redraw ran asks for another");
    }
}
