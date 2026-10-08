package dev.goldberry.css.image;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.bind.Subscription;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageAddress;
import dev.goldberry.log.Logs;

/// The state behind [StyleImages]' static half: the provider, what is on its
/// way, what failed, and who is told when a picture arrives.
///
/// Process-wide, like the image cache it fronts. A stylesheet names finitely
/// many addresses, so the two sets are bounded by the sheets an application
/// loads.
final class Registry {

    private static final Logger LOG = Logs.of(StyleImages.class);

    /// The addresses whose loads are running, so a load is watched once however
    /// many frames ask while it runs.
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    /// The addresses that could not be read. Not asked for again: a missing file
    /// asked about on every frame would be a file read and a warning per frame.
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private static final AtomicLong GENERATION = new AtomicLong();

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile @Nullable StyleImages provider;

    private Registry() {}

    static StyleImages provider() {
        var found = provider;
        if (found == null) {
            found = ServiceLoader.load(StyleImages.class).findFirst().orElseGet(DirectStyleImages::new);
            provider = found;
        }
        return found;
    }

    static long generation() {
        return GENERATION.get();
    }

    static Subscription onArrival(Runnable listener) {
        LISTENERS.add(listener);
        return () -> {
            var _ = LISTENERS.remove(listener);
        };
    }

    static @Nullable StyleImage resolve(CssImage.Url url, double scale) {
        var address = url.address();
        if (scale > 1) {
            var doubled = attempt(address.atDensity(2), false);
            var pixels = doubled.image();
            if (pixels != null) {
                return new StyleImage(pixels, 2);
            }
            if (doubled.pending()) {
                return null;
            }
        }
        var pixels = attempt(address, true).image();
        return pixels == null ? null : new StyleImage(pixels, 1);
    }

    /// Forgets what failed, for a test that makes a missing file appear.
    static void forgetFailures() {
        FAILED.clear();
    }

    /// Puts `value` in front of the service loader's answer, for a test that
    /// answers loads by hand; null goes back to the service loader.
    static void use(@Nullable StyleImages value) {
        provider = value;
        FAILED.clear();
        PENDING.clear();
    }

    /// Where one address stands.
    ///
    /// @param image   the pixels, when they are here
    /// @param pending whether they are on their way
    private record Attempt(@Nullable Image image, boolean pending) {

        static final Attempt FAILED = new Attempt(null, false);
        static final Attempt PENDING = new Attempt(null, true);
    }

    /// Asks the provider for `address`.
    ///
    /// @param required whether a failure is worth a warning: the 1x picture is
    ///                 what the stylesheet named, and a `@2x` variant is only
    ///                 looked for
    private static Attempt attempt(ImageAddress address, boolean required) {
        var key = address.toString();
        if (FAILED.contains(key)) {
            return Attempt.FAILED;
        }
        CompletableFuture<Image> future;
        try {
            future = provider().load(address);
        } catch (RuntimeException e) {
            failed(key, e, required);
            return Attempt.FAILED;
        }
        if (future.isDone()) {
            try {
                return new Attempt(future.join(), false);
            } catch (RuntimeException e) {
                failed(key, e, required);
                return Attempt.FAILED;
            }
        }
        if (PENDING.add(key)) {
            var _ = future.whenComplete((image, failure) -> {
                PENDING.remove(key);
                if (failure != null) {
                    failed(key, failure, required);
                }
                // A failed 2x variant is news too: the 1x picture is asked for next.
                GENERATION.incrementAndGet();
                for (var listener : LISTENERS) {
                    listener.run();
                }
            });
        }
        return Attempt.PENDING;
    }

    private static void failed(String key, Throwable failure, boolean required) {
        if (!FAILED.add(key)) {
            return;
        }
        var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (required) {
            LOG.warn("the stylesheet's image {} did not load: {}", key, cause.getMessage());
        } else {
            LOG.debug("no variant {}: {}", key, cause.getMessage());
        }
    }
}
