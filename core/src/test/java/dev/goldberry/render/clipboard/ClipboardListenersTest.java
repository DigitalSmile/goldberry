package dev.goldberry.render.clipboard;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.Subscription;

@DisplayName("the clipboard's listeners")
class ClipboardListenersTest {

    private final ClipboardListeners listeners = new ClipboardListeners();

    @Test
    @DisplayName("tells every listener, until its subscription is closed")
    void tellsUntilClosed() {
        var told = new AtomicInteger();
        var subscription = listeners.add(told::incrementAndGet);

        listeners.changed();
        subscription.close();
        subscription.close();
        listeners.changed();

        assertAll(() -> assertEquals(1, told.get()), () -> assertEquals(0, listeners.size()));
    }

    @Test
    @DisplayName("one listener added twice is two subscriptions")
    void addedTwice() {
        var told = new AtomicInteger();
        Runnable listener = told::incrementAndGet;
        var first = listeners.add(listener);
        listeners.add(listener);

        first.close();
        listeners.changed();

        assertEquals(1, told.get());
    }

    @Test
    @DisplayName("a listener that throws does not keep the others stale")
    void aFailureIsIsolated() {
        var order = new ArrayList<String>();
        listeners.add(() -> order.add("first"));
        listeners.add(() -> {
            throw new IllegalStateException("broken paste button");
        });
        listeners.add(() -> order.add("third"));

        listeners.changed();

        assertEquals(List.of("first", "third"), order);
    }

    @Test
    @DisplayName("a listener may close itself while it is being told")
    void closesItselfWhileTold() {
        var told = new AtomicInteger();
        var holder = new Subscription[1];
        holder[0] = listeners.add(() -> {
            told.incrementAndGet();
            holder[0].close();
        });

        listeners.changed();
        listeners.changed();

        assertEquals(1, told.get());
    }
}
