package io.github.digitalsmile.goldberry.bind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The observable value of §9 (ADR-0062).
class PropertyTest {

    @Test
    @DisplayName("a property hands back what it was given")
    void holdsItsValue() {
        assertEquals("frost", Property.of("frost").get());
        assertNull(Property.of(null).get(), "null is a value a binding has to be able to hold");
    }

    @Test
    @DisplayName("a listener is told what the value became")
    void notifiesOnChange() {
        var property = Property.of(1);
        var seen = new ArrayList<Integer>();
        property.subscribe(seen::add);

        assertTrue(property.set(2));
        assertTrue(property.set(3));

        assertEquals(List.of(2, 3), seen);
    }

    @Test
    @DisplayName("subscribing does not fire, because the subscriber can read")
    void doesNotFireOnSubscribe() {
        var property = Property.of("a");
        var seen = new ArrayList<String>();

        property.subscribe(seen::add);

        // A widget subscribes while it is being built. Firing here would mark it
        // as needing a rebuild before its first build had finished.
        assertEquals(List.of(), seen);
        assertEquals("a", property.get());
    }

    @Test
    @DisplayName("setting the value it already has notifies nobody")
    void unchangedIsNotAChange() {
        var property = Property.of("frost");
        var calls = new int[1];
        property.subscribe(value -> calls[0]++);

        assertFalse(property.set("frost"));
        assertFalse(property.set(new String("frost")), "equal, not identical, is still unchanged");

        assertEquals(0, calls[0]);
    }

    @Test
    @DisplayName("null is a change in both directions")
    void nullIsAValue() {
        var property = Property.of("a");
        var seen = new ArrayList<String>();
        property.subscribe(seen::add);

        assertTrue(property.set(null));
        assertFalse(property.set(null));
        assertTrue(property.set("b"));

        assertEquals(java.util.Arrays.asList(null, "b"), seen);
    }

    @Test
    @DisplayName("a closed subscription stops hearing, and closing twice is fine")
    void unsubscribes() {
        var property = Property.of(0);
        var seen = new ArrayList<Integer>();
        var subscription = property.subscribe(seen::add);

        property.set(1);
        subscription.close();
        property.set(2);
        subscription.close();

        assertEquals(List.of(1), seen);
        assertEquals(0, property.listenerCount(), "a closed subscription must not leave a listener");
    }

    @Nested
    @DisplayName("while it is notifying")
    class WhileNotifying {

        @Test
        @DisplayName("a listener that unsubscribes does not disturb the others")
        void unsubscribeDuringNotify() {
            var property = Property.of(0);
            var seen = new ArrayList<String>();
            var first = new Subscription[1];

            first[0] = property.subscribe(value -> {
                seen.add("first:" + value);
                first[0].close();
            });
            property.subscribe(value -> seen.add("second:" + value));

            property.set(1);
            property.set(2);

            // The second listener still heard the change the first one left
            // during -- the notification runs over a snapshot.
            assertEquals(List.of("first:1", "second:1", "second:2"), seen);
        }

        @Test
        @DisplayName("two properties mirroring each other settle instead of recursing")
        void mirroringTerminates() {
            var left = Property.of("a");
            var right = Property.of("a");
            left.subscribe(right::set);
            right.subscribe(left::set);

            // The second set finds the value already there and stops. Without
            // that rule this is a StackOverflowError, and two-way binding is
            // exactly the shape that produces it.
            left.set("b");

            assertEquals("b", left.get());
            assertEquals("b", right.get());
        }
    }
}
