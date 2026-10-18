package dev.goldberry.platform;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// A start that asks about the system theme must not open WebKit to answer it.
/// Each probe here counts its calls, which stands in for the library being
/// opened.
@DisplayName("the lazily probed capabilities")
class LazyCapabilitiesTest {

    private final AtomicInteger bitReads = new AtomicInteger();
    private final AtomicInteger webViewProbes = new AtomicInteger();
    private final AtomicInteger notificationProbes = new AtomicInteger();

    private LazyCapabilities capabilities(boolean webView, boolean notifications) {
        Supplier<Set<Capability>> bits = () -> {
            bitReads.incrementAndGet();
            return Set.of(Capability.SYSTEM_THEME, Capability.FILE_DIALOG);
        };
        BooleanSupplier web = () -> {
            webViewProbes.incrementAndGet();
            return webView;
        };
        BooleanSupplier notify = () -> {
            notificationProbes.incrementAndGet();
            return notifications;
        };
        return new LazyCapabilities(bits, Map.of(Capability.WEB_VIEW, web, Capability.NOTIFICATIONS, notify));
    }

    @Test
    @DisplayName("asking about another capability does not open the web view library")
    void anotherCapabilityLeavesTheWebViewAlone() {
        var capabilities = capabilities(true, true);

        assertAll(
                () -> assertTrue(capabilities.contains(Capability.SYSTEM_THEME)),
                () -> assertFalse(capabilities.contains(Capability.INPUT_METHOD)),
                () -> assertTrue(capabilities.contains(Capability.FILE_DIALOG)),
                () -> assertEquals(0, webViewProbes.get(), "the web view was probed"),
                () -> assertEquals(0, notificationProbes.get(), "notifications were probed"));
    }

    @Test
    @DisplayName("probes the web view once, on the first question about it")
    void theWebViewIsProbedOnce() {
        var capabilities = capabilities(true, false);

        assertTrue(capabilities.contains(Capability.WEB_VIEW));
        assertTrue(capabilities.contains(Capability.WEB_VIEW));
        assertAll(
                () -> assertEquals(1, webViewProbes.get()),
                () -> assertEquals(0, bitReads.get(), "the bits are not needed to answer a probed member"));
    }

    @Test
    @DisplayName("reads the bits once, however often they are asked about")
    void theBitsAreReadOnce() {
        var capabilities = capabilities(false, false);

        for (var capability : Capability.values()) {
            if (capability != Capability.WEB_VIEW && capability != Capability.NOTIFICATIONS) {
                capabilities.contains(capability);
            }
        }
        assertEquals(1, bitReads.get());
    }

    @Test
    @DisplayName("a probed member is answered by its probe, never by the bits")
    void aProbeOverridesTheBits() {
        var capabilities =
                new LazyCapabilities(() -> Set.of(Capability.WEB_VIEW), Map.of(Capability.WEB_VIEW, () -> false));

        assertAll(
                () -> assertFalse(capabilities.contains(Capability.WEB_VIEW)),
                () -> assertEquals(Set.of(), Set.copyOf(capabilities)));
    }

    @Test
    @DisplayName("iterating answers every member, in declaration order")
    void iteratingAnswersEverything() {
        var capabilities = capabilities(true, false);

        var listed = new ArrayList<Capability>();
        capabilities.forEach(listed::add);

        assertAll(
                () -> assertEquals(
                        List.of(Capability.SYSTEM_THEME, Capability.FILE_DIALOG, Capability.WEB_VIEW), listed),
                () -> assertEquals(3, capabilities.size()),
                () -> assertEquals(
                        Set.of(Capability.SYSTEM_THEME, Capability.FILE_DIALOG, Capability.WEB_VIEW), capabilities),
                () -> assertEquals(1, webViewProbes.get()),
                () -> assertEquals(1, notificationProbes.get()));
    }

    @Test
    @DisplayName("is unmodifiable, through the set and through its iterator")
    void unmodifiable() {
        var capabilities = capabilities(true, true);

        assertAll(
                () -> assertThrows(UnsupportedOperationException.class, () -> capabilities.add(Capability.WAYLAND)),
                () -> assertThrows(
                        UnsupportedOperationException.class, () -> capabilities.remove(Capability.SYSTEM_THEME)),
                () -> assertThrows(UnsupportedOperationException.class, () -> {
                    var iterator = capabilities.iterator();
                    iterator.next();
                    iterator.remove();
                }));
    }

    @Test
    @DisplayName("answers false for anything that is not a capability")
    void notACapability() {
        var capabilities = capabilities(true, true);

        assertAll(
                () -> assertFalse(capabilities.contains("WEB_VIEW")),
                () -> assertFalse(capabilities.contains(null)),
                () -> assertEquals(0, webViewProbes.get()));
    }

    @Test
    @Timeout(10)
    @DisplayName("two threads asking together probe once")
    void concurrentQuestionsProbeOnce() throws InterruptedException {
        var capabilities = capabilities(true, false);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var i = 0; i < 16; i++) {
                executor.submit(() -> {
                    start.await();
                    return capabilities.contains(Capability.WEB_VIEW);
                });
            }
            start.countDown();
        }
        assertEquals(1, webViewProbes.get());
    }
}
