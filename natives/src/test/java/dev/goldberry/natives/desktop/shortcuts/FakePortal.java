package dev.goldberry.natives.desktop.shortcuts;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.dbus.Dbus;
import dev.goldberry.natives.desktop.dbus.PrivateBus;

/// A desktop portal of the test's own with a GlobalShortcuts interface, on a
/// bus of the test's own (see [PrivateBus]), so the desktop of whoever runs the
/// suite is never asked anything.
///
/// Each request is answered at once with what the test decided the user does:
/// grant every shortcut, grant some, choose other triggers, or cancel. The
/// test presses and releases a shortcut through a control interface of the
/// fake's own, which a second connection calls, because the serving
/// connection belongs to the serving thread.
final class FakePortal implements AutoCloseable {

    static final String CONTROL = "dev.goldberry.test.FakePortal";

    /// What the user does with the bind dialog.
    enum Answer {
        GRANT,
        CANCEL
    }

    volatile Answer answer = Answer.GRANT;

    /// The ids the user grants when not every one, or null for all.
    volatile @Nullable Set<String> granted;

    /// The trigger the user chose, by id; the one asked for otherwise.
    final Map<String, String> chosen = new ConcurrentHashMap<>();

    /// Whether the portal has the GlobalShortcuts interface at all.
    volatile boolean hasShortcuts = true;

    final List<String> registered = new CopyOnWriteArrayList<>();
    final List<String> sessionsCreated = new CopyOnWriteArrayList<>();
    final List<String> sessionsClosed = new CopyOnWriteArrayList<>();
    final List<Map<String, Map<String, Object>>> binds = new CopyOnWriteArrayList<>();

    private final PrivateBus bus;
    private final AtomicInteger next = new AtomicInteger();
    private volatile @Nullable String session;

    private FakePortal(PrivateBus bus) {
        this.bus = bus;
    }

    /// A bus with the portal on it, or null where this machine has no
    /// `dbus-daemon` or no libdbus.
    static @Nullable FakePortal start(Path directory) throws IOException {
        var bus = PrivateBus.start(directory);
        if (bus == null) {
            return null;
        }
        var portal = new FakePortal(bus);
        if (!bus.serve(PortalHotkeys.DESTINATION, _ -> {}, portal::handle)) {
            bus.close();
            return null;
        }
        return portal;
    }

    String address() {
        return bus.address();
    }

    /// The user presses the shortcut `id`.
    void press(String id) {
        control("Activate", id);
    }

    /// The user lets go of the shortcut `id`.
    void release(String id) {
        control("Deactivate", id);
    }

    private void control(String member, String id) {
        try (var client = bus.connect()) {
            var answer = client.invoke(
                    PortalHotkeys.DESTINATION,
                    "/test",
                    CONTROL,
                    member,
                    arguments -> arguments.string(id),
                    Duration.ofSeconds(5));
            if (!(answer instanceof Dbus.Reply.Returned)) {
                throw new IllegalStateException(member + " " + id + ": " + answer);
            }
        }
    }

    private void handle(Dbus.Incoming call) {
        if (!call.isCall()) {
            return;
        }
        var arguments = call.arguments();
        switch (call.iface() + "." + call.member()) {
            case PortalHotkeys.PROPERTIES + ".Get" -> {
                if (hasShortcuts && PortalHotkeys.SHORTCUTS.equals(arguments.getFirst())) {
                    call.reply(reply -> reply.variant("u", value -> value.uint32(1)));
                } else {
                    call.fail("org.freedesktop.DBus.Error.UnknownInterface", "no such interface");
                }
            }
            case PortalHotkeys.REGISTRY + ".Register" -> {
                registered.add((String) arguments.getFirst());
                call.reply(_ -> {});
            }
            case PortalHotkeys.SHORTCUTS + ".CreateSession" -> {
                var options = PortalHotkeys.dictionary(arguments.getFirst());
                var request = requestPath(options);
                var handle = "/org/freedesktop/portal/desktop/session/1_1/" + options.get("session_handle_token");
                sessionsCreated.add(handle);
                call.reply(reply -> reply.objectPath(request));
                if (answer == Answer.CANCEL) {
                    respond(request, 1, _ -> {});
                } else {
                    session = handle;
                    // The specification types it a string, for history's sake.
                    respond(request, 0, results -> results.entry("session_handle", "s", v -> v.string(handle)));
                }
            }
            case PortalHotkeys.SHORTCUTS + ".BindShortcuts" -> {
                var handle = ((Dbus.ObjectPath) arguments.get(0)).value();
                var asked = new LinkedHashMap<String, Map<String, Object>>();
                for (var shortcut : (List<?>) arguments.get(1)) {
                    var fields = ((Dbus.Struct) shortcut).fields();
                    asked.put((String) fields.get(0), PortalHotkeys.dictionary(fields.get(1)));
                }
                binds.add(asked);
                var request = requestPath(PortalHotkeys.dictionary(arguments.get(3)));
                call.reply(reply -> reply.objectPath(request));
                if (!handle.equals(session)) {
                    respond(request, 2, _ -> {});
                    return;
                }
                var some = granted;
                respond(
                        request,
                        0,
                        results -> results.entry(
                                "shortcuts",
                                "a(sa{sv})",
                                value -> value.array(
                                        "(sa{sv})",
                                        list -> asked.forEach((id, options) -> {
                                            if (some == null || some.contains(id)) {
                                                var trigger = chosen.getOrDefault(
                                                        id, (String) options.get("preferred_trigger"));
                                                list.struct(entry -> entry.string(id)
                                                        .array(
                                                                "{sv}",
                                                                shown -> shown.entry(
                                                                                "description",
                                                                                "s",
                                                                                v -> v.string((String)
                                                                                        options.get("description")))
                                                                        .entry(
                                                                                "trigger_description",
                                                                                "s",
                                                                                v -> v.string(trigger))));
                                            }
                                        }))));
            }
            case PortalHotkeys.SESSION + ".Close" -> {
                sessionsClosed.add(call.path());
                if (call.path().equals(session)) {
                    session = null;
                }
                call.reply(_ -> {});
            }
            case CONTROL + ".Activate" -> signal(call, "Activated");
            case CONTROL + ".Deactivate" -> signal(call, "Deactivated");
            default -> call.fail("org.freedesktop.DBus.Error.UnknownMethod", call.member());
        }
    }

    private void signal(Dbus.Incoming call, String member) {
        var id = (String) call.arguments().getFirst();
        var handle = session;
        call.reply(_ -> {});
        if (handle != null) {
            bus.service()
                    .signal(
                            PortalHotkeys.PATH,
                            PortalHotkeys.SHORTCUTS,
                            member,
                            arguments -> arguments
                                    .objectPath(handle)
                                    .string(id)
                                    .int64(1234)
                                    .array("{sv}", _ -> {}));
        }
    }

    private String requestPath(Map<String, Object> options) {
        var token = options.getOrDefault("handle_token", "t" + next.incrementAndGet());
        return "/org/freedesktop/portal/desktop/request/1_1/" + token;
    }

    private void respond(String request, int code, Consumer<Dbus.Writer> results) {
        bus.service()
                .signal(
                        request,
                        PortalHotkeys.REQUEST,
                        "Response",
                        arguments -> arguments.uint32(code).array("{sv}", results));
    }

    @Override
    public void close() {
        bus.close();
    }
}
