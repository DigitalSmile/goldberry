package dev.goldberry.natives.desktop.notify;

import java.util.ArrayList;
import java.util.List;

/// What the notification-area icon's window procedure hears about the balloon,
/// kept until [WindowsNotifier#poll()] asks.
///
/// The icon shows one balloon at a time, so an event is about whichever was
/// showing: a click activates it, and a timeout or a hide closes it. A new
/// balloon over one still showing closes the old one here, at once, because
/// Windows says nothing more about it.
///
/// The messages arrive in the legacy form, without `NOTIFYICON_VERSION_4`:
/// `lParam` is the event itself.
final class BalloonEvents {

    /// `WM_APP + 1`: the message the icon is registered to send.
    static final int CALLBACK = 0x8001;

    /// `NIN_BALLOONSHOW`, `NIN_BALLOONHIDE`, `NIN_BALLOONTIMEOUT` and
    /// `NIN_BALLOONUSERCLICK`: `WM_USER + 2` to `+ 5`.
    static final int SHOW = 0x0402;

    static final int HIDE = 0x0403;
    static final int TIMEOUT = 0x0404;
    static final int USER_CLICK = 0x0405;

    private final List<WindowsNotifier.Event> events = new ArrayList<>();
    private long showing;

    /// Balloon `id` is up now.
    synchronized void shown(long id) {
        if (showing != 0) {
            events.add(new WindowsNotifier.Event(showing, false));
        }
        showing = id;
    }

    /// Hears one window message. True when it was the icon's, which the
    /// window procedure then answers itself.
    synchronized boolean message(int message, long lParam) {
        if (message != CALLBACK) {
            return false;
        }
        var event = (int) (lParam & 0xFFFF);
        if (showing != 0) {
            switch (event) {
                case USER_CLICK -> {
                    events.add(new WindowsNotifier.Event(showing, true));
                    showing = 0;
                }
                case HIDE, TIMEOUT -> {
                    events.add(new WindowsNotifier.Event(showing, false));
                    showing = 0;
                }
                default -> {
                    // A show, or the mouse over the icon itself.
                }
            }
        }
        return true;
    }

    /// What happened since the last call.
    synchronized List<WindowsNotifier.Event> drain() {
        var drained = List.copyOf(events);
        events.clear();
        return drained;
    }
}
