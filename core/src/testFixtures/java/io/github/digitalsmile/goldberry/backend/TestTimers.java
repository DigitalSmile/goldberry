package io.github.digitalsmile.goldberry.backend;

/// An [EventLoop.Timer] for a test that fakes an event loop.
///
/// `EventLoop.Timer`'s constructor is package-private on purpose — a caller gets
/// one from `after` and never builds one — so this lives in its package and hands
/// them out to tests that do not. `TestFrames` has exactly this arrangement over
/// `Frame`, for exactly this reason: widening the constructor to public would put
/// a scheduling internal in the toolkit's API for the sake of a test helper.
///
/// A widget under test schedules through a stub `Host`, which needs something to
/// return; and the interesting assertion is usually that the timer was
/// **cancelled**, because a timer outliving the tree that scheduled it is one of
/// the two leaks a widget can cause.
public final class TestTimers {

    private TestTimers() {
    }

    /// A timer that is never due and never fires, and whose
    /// [EventLoop.Timer#isPending()] answers whether anything cancelled it.
    ///
    /// Never due because a test drives the action itself: firing on a wall clock
    /// would make the test wait out whatever interval the widget asked for.
    public static EventLoop.Timer pending() {
        return new EventLoop.Timer(Long.MAX_VALUE, () -> { });
    }
}
