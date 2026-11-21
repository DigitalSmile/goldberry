package dev.goldberry.natives.desktop.shortcuts;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.desktop.dbus.Dbus;

/// Global shortcuts on Wayland: `org.freedesktop.portal.GlobalShortcuts`, over
/// a private D-Bus connection of this backend's own thread.
///
/// A Wayland compositor gives no client the keyboard while another has focus,
/// so the only way to a global shortcut is to ask the desktop, and the
/// desktop asks the user. The conversation is the portal's usual one: a call
/// answers with a request object, and the answer comes later as that
/// request's `Response` signal, after a dialog the user may take a while over.
///
/// 1. `CreateSession`, whose response names the session.
/// 2. `BindShortcuts` with each shortcut's id, description and preferred
///    trigger, whose response lists the ones the user granted, each with the
///    trigger the user chose, which may not be the one asked for.
/// 3. `Activated` and `Deactivated` signals, for the session, by id: the press
///    and the release.
///
/// A second bind closes the session and starts another, because the portal
/// binds a session's shortcuts once.
///
/// An application the desktop did not start from a sandbox is identified by
/// `org.freedesktop.host.portal.Registry.Register`, with its desktop entry id,
/// before anything else is said on the connection. The portal lists its
/// shortcuts under that name. A portal too old for the registry answers with
/// an error, which is ignored: it falls back to what it can find out itself.
///
/// The thread sleeps in `poll` on the connection's socket and a wake pipe.
final class PortalHotkeys extends HotkeyWorker {

    static final String DESTINATION = "org.freedesktop.portal.Desktop";
    static final String PATH = "/org/freedesktop/portal/desktop";
    static final String SHORTCUTS = "org.freedesktop.portal.GlobalShortcuts";
    static final String REQUEST = "org.freedesktop.portal.Request";
    static final String SESSION = "org.freedesktop.portal.Session";
    static final String REGISTRY = "org.freedesktop.host.portal.Registry";
    static final String PROPERTIES = "org.freedesktop.DBus.Properties";

    /// How long a call may take to be answered. The answer is only the request
    /// object, which comes at once; the user's answer is the signal after it.
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(5);

    /// The response codes: granted, the user cancelled, anything else.
    private static final int RESPONSE_SUCCESS = 0;

    private static final int RESPONSE_CANCELLED = 1;

    private static final Logger LOG = Logs.of(PortalHotkeys.class);

    private final Supplier<Optional<Dbus>> connect;
    private final @Nullable String applicationId;
    private final Map<String, BiConsumer<Integer, Map<String, Object>>> requests = new HashMap<>();
    private @Nullable Dbus bus;
    private @Nullable PollWakeup wakeup;
    private int fd = -1;
    private int version;
    private int nextToken;
    private @Nullable String session;
    private @Nullable Pending pending;

    /// A bind waiting for the user.
    private record Pending(List<Hotkey> hotkeys, CompletableFuture<List<HotkeyOutcome>> outcome) {}

    private PortalHotkeys(Supplier<Optional<Dbus>> connect, @Nullable String applicationId) {
        super("goldberry-global-shortcuts-portal");
        this.connect = connect;
        this.applicationId = applicationId;
    }

    /// The portal on the session bus, or empty where there is no libdbus or no
    /// bus. A bus whose portal has no GlobalShortcuts still opens, and refuses
    /// every bind as unsupported.
    static Optional<PortalHotkeys> session(@Nullable String applicationId) {
        return start(new PortalHotkeys(Dbus::session, applicationId));
    }

    /// The portal on the bus at `address`, for a test with a portal of its own.
    static Optional<PortalHotkeys> at(String address, @Nullable String applicationId) {
        return start(new PortalHotkeys(() -> Dbus.at(address), applicationId));
    }

    private static Optional<PortalHotkeys> start(PortalHotkeys backend) {
        if (!Dbus.isAvailable()) {
            return Optional.empty();
        }
        return backend.start() ? Optional.of(backend) : Optional.empty();
    }

    @Override
    public String name() {
        return "GlobalShortcuts portal";
    }

    /// The portal interface's version, 0 where it has none.
    int version() {
        return version;
    }

    @Override
    boolean openOnThread() {
        var connection = connect.get().orElse(null);
        if (connection == null) {
            return false;
        }
        bus = connection;
        if (applicationId != null && !applicationId.isBlank()) {
            var registered = connection.invoke(
                    DESTINATION,
                    PATH,
                    REGISTRY,
                    "Register",
                    arguments -> arguments.string(applicationId).array("{sv}", _ -> {}),
                    CALL_TIMEOUT);
            LOG.debug("registered as {} with the portal: {}", applicationId, registered);
        }
        connection.match("type='signal',sender='" + DESTINATION + "',interface='" + REQUEST + "',member='Response'");
        connection.match("type='signal',sender='" + DESTINATION + "',interface='" + SHORTCUTS + "'");
        version = readVersion(connection);
        wakeup = PollWakeup.open().orElse(null);
        fd = connection.unixFd().orElse(-1);
        return true;
    }

    private static int readVersion(Dbus connection) {
        var answer = connection.invoke(
                DESTINATION,
                PATH,
                PROPERTIES,
                "Get",
                arguments -> arguments.string(SHORTCUTS).string("version"),
                CALL_TIMEOUT);
        if (answer instanceof Dbus.Reply.Returned(var values)
                && !values.isEmpty()
                && values.getFirst() instanceof Dbus.Variant(Integer value)) {
            return value;
        }
        LOG.debug("this desktop's portal has no GlobalShortcuts: {}", answer);
        return 0;
    }

    @Override
    void awaitEvents() {
        var connection = bus;
        if (connection == null) {
            return;
        }
        connection.receive(0, this::incoming);
        var pipe = wakeup;
        if (pipe != null && fd >= 0) {
            if (pipe.await(fd, -1)) {
                connection.receive(0, this::incoming);
            }
        } else {
            connection.receive(100, this::incoming);
        }
    }

    @Override
    void wake() {
        var pipe = wakeup;
        if (pipe != null) {
            pipe.wake();
        }
    }

    @Override
    void bindOnThread(List<Hotkey> hotkeys, HotkeyState state, CompletableFuture<List<HotkeyOutcome>> outcome) {
        var earlier = pending;
        if (earlier != null) {
            pending = null;
            earlier.outcome()
                    .complete(HotkeyBackend.refuseAll(
                            earlier.hotkeys(), HotkeyOutcome.Refusal.UNSUPPORTED, "a later bind replaced this one"));
        }
        closeSession();
        if (hotkeys.isEmpty()) {
            outcome.complete(List.of());
            return;
        }
        if (version == 0) {
            outcome.complete(HotkeyBackend.refuseAll(
                    hotkeys, HotkeyOutcome.Refusal.UNSUPPORTED, "this desktop's portal has no GlobalShortcuts"));
            return;
        }
        var bind = new Pending(hotkeys, outcome);
        pending = bind;
        var sessionToken = token();
        var requested = request(
                "CreateSession",
                arguments -> arguments.array(
                        "{sv}",
                        options -> options.entry("handle_token", "s", value -> value.string(token()))
                                .entry("session_handle_token", "s", value -> value.string(sessionToken))));
        if (requested == null) {
            finish(bind, HotkeyBackend.refuseAll(hotkeys, HotkeyOutcome.Refusal.UNSUPPORTED, "CreateSession failed"));
            return;
        }
        requests.put(requested, (code, results) -> created(bind, code, results));
    }

    private void created(Pending bind, int code, Map<String, Object> results) {
        if (pending != bind) {
            return;
        }
        if (code != RESPONSE_SUCCESS) {
            finish(bind, refusedFor(bind.hotkeys(), code));
            return;
        }
        var handle = switch (results.get("session_handle")) {
            case String text -> text;
            case Dbus.ObjectPath(var text) -> text;
            case null, default -> null;
        };
        if (handle == null) {
            finish(
                    bind,
                    HotkeyBackend.refuseAll(
                            bind.hotkeys(), HotkeyOutcome.Refusal.UNSUPPORTED, "the portal named no session"));
            return;
        }
        session = handle;
        var requested = request(
                "BindShortcuts",
                arguments -> arguments
                        .objectPath(handle)
                        .array("(sa{sv})", shortcuts -> {
                            for (var hotkey : bind.hotkeys()) {
                                shortcuts.struct(shortcut -> shortcut.string(hotkey.id())
                                        .array(
                                                "{sv}",
                                                options -> options.entry(
                                                                "description",
                                                                "s",
                                                                value -> value.string(hotkey.description()))
                                                        .entry(
                                                                "preferred_trigger",
                                                                "s",
                                                                value -> value.string(hotkey.xdgTrigger()))));
                            }
                        })
                        .string("")
                        .array("{sv}", options -> options.entry("handle_token", "s", value -> value.string(token()))));
        if (requested == null) {
            finish(
                    bind,
                    HotkeyBackend.refuseAll(bind.hotkeys(), HotkeyOutcome.Refusal.UNSUPPORTED, "BindShortcuts failed"));
            return;
        }
        requests.put(requested, (answer, bound) -> bound(bind, answer, bound));
    }

    private void bound(Pending bind, int code, Map<String, Object> results) {
        if (pending != bind) {
            return;
        }
        if (code != RESPONSE_SUCCESS) {
            finish(bind, refusedFor(bind.hotkeys(), code));
            return;
        }
        finish(bind, outcomes(bind.hotkeys(), results.get("shortcuts")));
    }

    /// What `BindShortcuts`' response says of each hotkey: granted with the
    /// user's trigger when it lists it, denied when it does not.
    static List<HotkeyOutcome> outcomes(List<Hotkey> hotkeys, @Nullable Object listed) {
        var granted = new LinkedHashMap<String, String>();
        if (listed instanceof List<?> shortcuts) {
            for (var shortcut : shortcuts) {
                if (shortcut instanceof Dbus.Struct(var fields)
                        && fields.size() == 2
                        && fields.getFirst() instanceof String id) {
                    var trigger = dictionary(fields.get(1)).get("trigger_description");
                    granted.put(id, trigger instanceof String text ? text : "");
                }
            }
        }
        return hotkeys.stream()
                .<HotkeyOutcome>map(hotkey -> {
                    var trigger = granted.get(hotkey.id());
                    if (trigger == null) {
                        return new HotkeyOutcome.Refused(
                                hotkey.id(), HotkeyOutcome.Refusal.DENIED, "the user did not grant it");
                    }
                    return new HotkeyOutcome.Bound(hotkey.id(), trigger.isBlank() ? hotkey.xdgTrigger() : trigger);
                })
                .toList();
    }

    private static List<HotkeyOutcome> refusedFor(List<Hotkey> hotkeys, int code) {
        return code == RESPONSE_CANCELLED
                ? HotkeyBackend.refuseAll(hotkeys, HotkeyOutcome.Refusal.DENIED, "the user cancelled the request")
                : HotkeyBackend.refuseAll(
                        hotkeys, HotkeyOutcome.Refusal.UNSUPPORTED, "the portal ended the request (" + code + ")");
    }

    private void finish(Pending bind, List<HotkeyOutcome> outcomes) {
        if (pending == bind) {
            pending = null;
        }
        bind.outcome().complete(outcomes);
    }

    private void incoming(Dbus.Incoming message) {
        if (message.isCall()) {
            return;
        }
        var arguments = message.arguments();
        switch (message.iface() + "." + message.member()) {
            case REQUEST + ".Response" -> {
                var continuation = requests.remove(message.path());
                if (continuation != null && arguments.size() >= 2 && arguments.getFirst() instanceof Integer code) {
                    continuation.accept(code, dictionary(arguments.get(1)));
                }
            }
            case SHORTCUTS + ".Activated" -> forSession(arguments, true);
            case SHORTCUTS + ".Deactivated" -> forSession(arguments, false);
            default -> {
                // ShortcutsChanged: the user rebound one in the desktop's
                // settings. The id is what is delivered, so nothing changes.
            }
        }
    }

    private void forSession(List<Object> arguments, boolean down) {
        var current = state();
        if (current == null || session == null || arguments.size() < 2) {
            return;
        }
        var handle = switch (arguments.getFirst()) {
            case Dbus.ObjectPath(var text) -> text;
            case String text -> text;
            default -> "";
        };
        if (handle.equals(session) && arguments.get(1) instanceof String id) {
            if (down) {
                current.pressed(id);
            } else {
                current.released(id);
            }
        }
    }

    /// A portal call that answers with a request object, and that object's
    /// path, or null when the call failed.
    private @Nullable String request(String member, Consumer<Dbus.Writer> arguments) {
        var connection = bus;
        if (connection == null) {
            return null;
        }
        var answer = connection.invoke(DESTINATION, PATH, SHORTCUTS, member, arguments, CALL_TIMEOUT);
        if (answer instanceof Dbus.Reply.Returned(var values)
                && !values.isEmpty()
                && values.getFirst() instanceof Dbus.ObjectPath(var path)) {
            return path;
        }
        LOG.debug("the portal's {} answered {}", member, answer);
        return null;
    }

    private void closeSession() {
        var open = session;
        var connection = bus;
        session = null;
        requests.clear();
        if (open != null && connection != null) {
            connection.invoke(DESTINATION, open, SESSION, "Close", _ -> {}, CALL_TIMEOUT);
        }
    }

    private String token() {
        return "goldberry" + (++nextToken);
    }

    /// An `a{sv}` as a map, with each variant read through.
    static Map<String, Object> dictionary(@Nullable Object value) {
        var map = new LinkedHashMap<String, Object>();
        if (value instanceof List<?> entries) {
            for (var entry : entries) {
                if (entry instanceof Dbus.Entry(String key, var held)) {
                    map.put(key, held instanceof Dbus.Variant(var inner) ? inner : held);
                }
            }
        }
        return map;
    }

    @Override
    void closeOnThread() {
        var waiting = pending;
        if (waiting != null) {
            finish(
                    waiting,
                    HotkeyBackend.refuseAll(
                            waiting.hotkeys(), HotkeyOutcome.Refusal.UNSUPPORTED, "the shortcuts were closed"));
        }
        closeSession();
        var connection = bus;
        bus = null;
        if (connection != null) {
            connection.close();
        }
        var pipe = wakeup;
        wakeup = null;
        if (pipe != null) {
            pipe.close();
        }
    }
}
