package dev.goldberry.render.desktop.notify;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// What a host posts notifications through: the backend's [BackendNotifier],
/// and the asking-again a click on Linux needs.
///
/// A click on a Linux notification is a D-Bus signal that waits on the bus
/// until something reads it, and nothing wakes the frame loop for it. So
/// while a posted notification could still be clicked, this asks the notifier
/// every [#POLL_INTERVAL] on the loop's own timer, and stops as soon as
/// nothing is left that could be — an application that posts nothing, or only
/// notifications without an action, schedules nothing at all.
///
/// UI-thread confined.
///
/// Read more: [Notifications](https://goldberry.dev/docs/guide/windows.html#notifications).
public final class NotificationCenter {

    /// How often a Linux click is looked for: quick enough that the window
    /// comes forward as if it were told, slow enough to cost nothing.
    public static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    /// The loop's timer, as a host has it.
    @FunctionalInterface
    public interface Scheduler {

        /// Runs `action` on the UI thread after `delay`.
        void after(Duration delay, Runnable action);
    }

    private final Supplier<BackendNotifier> notifier;
    private final Scheduler scheduler;
    private boolean polling;

    /// @param notifier  the backend's notifier, asked for on every post so a
    ///                  backend swapped under the host is followed
    /// @param scheduler the loop's timer
    public NotificationCenter(Supplier<BackendNotifier> notifier, Scheduler scheduler) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /// Posts `notification` under `application`'s name, and starts asking
    /// after its click if it has an action.
    ///
    /// @return whether the desktop took it
    public boolean post(String application, Notification notification) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(notification, "notification");
        var backend = notifier.get();
        var posted = backend.post(application, notification);
        if (posted && notification.onActivate() != null) {
            poll(backend);
        }
        return posted;
    }

    /// Sets or clears the dock or launcher badge — see [BackendNotifier#badge].
    public boolean badge(@Nullable String label) {
        return notifier.get().badge(label);
    }

    private void poll(BackendNotifier backend) {
        if (polling) {
            return;
        }
        polling = true;
        scheduler.after(POLL_INTERVAL, () -> {
            polling = false;
            if (backend.poll()) {
                poll(backend);
            }
        });
    }
}
