package dev.goldberry.natives.desktop.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Upcalls;

/// Notifications and the dock badge on macOS: `UNUserNotificationCenter` and
/// `NSDockTile`, through the Objective-C runtime.
///
/// ## A notification needs a bundle
///
/// macOS attributes a notification to an application **bundle** — the
/// `CFBundleIdentifier` in its `Info.plist` — and asks the user's permission
/// once per bundle. A process with no bundle identifier, which is every plain
/// `java` launch, has nothing to attribute to: `UNUserNotificationCenter`
/// raises an Objective-C exception for it, and the older
/// `NSUserNotificationCenter` answers nil. So [#post] answers null there
/// without asking, and an application that wants notifications on macOS is
/// packaged as an `.app`. The badge is the dock tile's and needs no bundle.
///
/// UNVERIFIED: written against Apple's documentation and the runtime's C API,
/// not yet run on a Mac.
public final class MacNotifier {

    private static final Logger LOG = Logs.of(MacNotifier.class);

    /// `UNAuthorizationOptionBadge | Sound | Alert`.
    private static final long AUTHORIZATION = 1 | 2 | 4;

    /// `UNNotificationPresentationOptionSound | Alert | List | Banner`: how a
    /// notification is shown while the application is in front, which by
    /// default it is not shown at all.
    private static final long PRESENT_IN_FRONT = 2 | 4 | 8 | 16;

    private static final String DEFAULT_ACTION = "com.apple.UNNotificationDefaultActionIdentifier";

    /// `void (*)(id self, SEL _cmd, id center, id what, id completionHandler)`.
    private static final FunctionDescriptor DELEGATE_METHOD =
            Upcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /// `void (^)(BOOL granted, NSError *error)`, as the block's invoke function.
    private static final FunctionDescriptor AUTHORIZED =
            Upcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, JAVA_BYTE, ADDRESS));

    private static final AtomicLong NEXT = new AtomicLong(1);

    private static @Nullable MacNotifier instance;

    private final ObjC objc;
    private @Nullable MemorySegment center;
    private Consumer<String> activated = identifier -> {};

    private MacNotifier(ObjC objc) {
        this.objc = objc;
    }

    /// The notifier, or empty off macOS.
    public static synchronized Optional<MacNotifier> get() {
        if (instance == null) {
            instance = ObjC.get().map(MacNotifier::new).orElse(null);
        }
        return Optional.ofNullable(instance);
    }

    /// Whether this process is a bundle macOS can attribute a notification to.
    public boolean hasBundle() {
        return objc.autoreleased(() -> {
            var bundle = objc.send(objc.cls("NSBundle"), "mainBundle");
            return bundle.address() != 0
                    && objc.send(bundle, "bundleIdentifier").address() != 0;
        });
    }

    /// Who to tell when the user clicks a notification, with the identifier
    /// [#post] answered.
    public void onActivated(Consumer<String> listener) {
        this.activated = listener;
    }

    /// Posts a notification, asking the user's permission the first time.
    ///
    /// @return its identifier, or null where it cannot be posted — no bundle,
    ///         or no notification center
    public @Nullable String post(String title, String body) {
        try {
            if (!hasBundle()) {
                return null;
            }
            return objc.autoreleased(() -> {
                var notifications = center();
                if (notifications == null) {
                    return null;
                }
                var identifier = "dev.goldberry.notification." + NEXT.getAndIncrement();
                var content = objc.send(objc.send(objc.cls("UNMutableNotificationContent"), "alloc"), "init");
                objc.sendVoid(content, "setTitle:", objc.string(title));
                objc.sendVoid(content, "setBody:", objc.string(body));
                var sound = objc.send(objc.cls("UNNotificationSound"), "defaultSound");
                objc.sendVoid(content, "setSound:", sound);
                var request = objc.send(
                        objc.cls("UNNotificationRequest"),
                        "requestWithIdentifier:content:trigger:",
                        objc.string(identifier),
                        content,
                        MemorySegment.NULL);
                objc.sendVoid(
                        notifications, "addNotificationRequest:withCompletionHandler:", request, MemorySegment.NULL);
                objc.sendVoid(content, "release");
                return identifier;
            });
        } catch (RuntimeException e) {
            LOG.debug("macOS would not post a notification: {}", e.toString());
            return null;
        }
    }

    /// Sets the dock icon's badge, or takes it away for null or empty.
    public boolean badge(@Nullable String label) {
        try {
            return objc.autoreleased(() -> {
                var application = objc.send(objc.cls("NSApplication"), "sharedApplication");
                var tile = objc.send(application, "dockTile");
                if (tile.address() == 0) {
                    return false;
                }
                objc.sendVoid(
                        tile,
                        "setBadgeLabel:",
                        label == null || label.isEmpty() ? MemorySegment.NULL : objc.string(label));
                return true;
            });
        } catch (RuntimeException e) {
            return false;
        }
    }

    /// The notification center with this process's delegate on it, asking
    /// for permission once. Null where the framework is not there.
    private @Nullable MemorySegment center() {
        if (center != null) {
            return center;
        }
        if (!ObjC.loadFramework("UserNotifications")) {
            return null;
        }
        var found = objc.send(objc.cls("UNUserNotificationCenter"), "currentNotificationCenter");
        if (found.address() == 0) {
            return null;
        }
        var delegateClass = objc.defineClass(
                "GoldberryNotificationDelegate",
                "NSObject",
                "UNUserNotificationCenterDelegate",
                Map.of(
                        "userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:",
                        new ObjC.Method(stub("received"), "v@:@@@?"),
                        "userNotificationCenter:willPresentNotification:withCompletionHandler:",
                        new ObjC.Method(stub("presenting"), "v@:@@@?")));
        // Never released: the center holds its delegate weakly.
        var delegate = objc.send(objc.send(delegateClass, "alloc"), "init");
        objc.sendVoid(found, "setDelegate:", delegate);
        objc.sendVoid(
                found,
                "requestAuthorizationWithOptions:completionHandler:",
                AUTHORIZATION,
                objc.globalBlock(authorizedStub()));
        center = found;
        return found;
    }

    /// `userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:`.
    /// **Called from Objective-C; must not throw.**
    @SuppressWarnings("unused")
    private static void received(
            MemorySegment self,
            MemorySegment command,
            MemorySegment center,
            MemorySegment response,
            MemorySegment handler) {
        var notifier = instance;
        if (notifier == null) {
            return;
        }
        try {
            var objc = notifier.objc;
            var action = objc.text(objc.send(response, "actionIdentifier"));
            var request = objc.send(objc.send(response, "notification"), "request");
            var identifier = objc.text(objc.send(request, "identifier"));
            if (DEFAULT_ACTION.equals(action) && identifier != null) {
                notifier.activated.accept(identifier);
            }
        } catch (Throwable t) {
            LOG.warn("a notification's click could not be delivered", t);
        } finally {
            try {
                notifier.objc.callBlock(handler);
            } catch (Throwable ignored) {
                // Nothing further to try.
            }
        }
    }

    /// `userNotificationCenter:willPresentNotification:withCompletionHandler:`:
    /// show it even though the application is in front. **Called from
    /// Objective-C; must not throw.**
    @SuppressWarnings("unused")
    private static void presenting(
            MemorySegment self,
            MemorySegment command,
            MemorySegment center,
            MemorySegment notification,
            MemorySegment handler) {
        var notifier = instance;
        if (notifier == null) {
            return;
        }
        try {
            notifier.objc.callBlock(handler, PRESENT_IN_FRONT);
        } catch (Throwable t) {
            LOG.warn("a notification could not be presented", t);
        }
    }

    /// The answer to the permission request. **Called from Objective-C; must
    /// not throw.**
    @SuppressWarnings("unused")
    private static void authorized(MemorySegment block, byte granted, MemorySegment error) {
        if (granted == 0) {
            LOG.info("the user has not allowed this application's notifications; macOS will not show them");
        }
    }

    @SuppressWarnings("restricted")
    private static MemorySegment stub(String name) {
        try {
            var target = MethodHandles.lookup()
                    .findStatic(
                            MacNotifier.class,
                            name,
                            MethodType.methodType(
                                    void.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class));
            return Linker.nativeLinker().upcallStub(target, DELEGATE_METHOD, Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no " + name + " method for the notification delegate", e);
        }
    }

    @SuppressWarnings("restricted")
    private static MemorySegment authorizedStub() {
        try {
            var target = MethodHandles.lookup()
                    .findStatic(
                            MacNotifier.class,
                            "authorized",
                            MethodType.methodType(void.class, MemorySegment.class, byte.class, MemorySegment.class));
            return Linker.nativeLinker().upcallStub(target, AUTHORIZED, Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no method for the permission answer", e);
        }
    }
}
