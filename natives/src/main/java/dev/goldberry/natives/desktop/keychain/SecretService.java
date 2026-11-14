package dev.goldberry.natives.desktop.keychain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.dbus.Dbus;
import dev.goldberry.natives.desktop.dbus.Dbus.ObjectPath;
import dev.goldberry.natives.desktop.dbus.Dbus.Reply;
import dev.goldberry.natives.desktop.keychain.SecretStoreException.Reason;

/// The freedesktop Secret Service over D-Bus, which gnome-keyring, KWallet
/// (5.97 and later) and KeePassXC serve. No libsecret: the protocol is a
/// handful of calls, and libdbus is what the notifier already binds.
///
/// ## What is stored
///
/// An item in the default collection (the "login" keyring), labelled for the
/// store's own viewer, with the attributes `service` and `username`. They
/// are the attributes `secret-tool` and other libraries' keyrings use, so
/// `secret-tool lookup service tessera username me` finds what this stored.
/// The schema attribute is `org.freedesktop.Secret.Generic`, libsecret's
/// own.
///
/// ## Locked keyrings
///
/// A keyring that is locked is unlocked through the service's prompt, which
/// is a window of the keyring's own asking for the password. The call waits
/// for the prompt's answer for up to the prompt timeout. A prompt dismissed, or
/// left unanswered past the timeout, is [Reason#DENIED].
///
/// ## The session
///
/// The `plain` algorithm: the secret crosses the session bus as it is. The bus
/// is the user's own and is not readable by other users. Anything running as
/// the same user can read the keyring itself once it is unlocked, so an
/// encrypted session would protect against nothing that matters here, and
/// would need a Diffie-Hellman exchange this binding does not carry.
public final class SecretService implements SecretStore {

    static final String NAME = "org.freedesktop.secrets";
    static final String PATH = "/org/freedesktop/secrets";
    static final String SERVICE = "org.freedesktop.Secret.Service";
    static final String COLLECTION = "org.freedesktop.Secret.Collection";
    static final String ITEM = "org.freedesktop.Secret.Item";
    static final String SESSION = "org.freedesktop.Secret.Session";
    static final String PROMPT = "org.freedesktop.Secret.Prompt";
    static final String LABEL = "org.freedesktop.Secret.Item.Label";
    static final String ATTRIBUTES = "org.freedesktop.Secret.Item.Attributes";
    static final String SCHEMA = "org.freedesktop.Secret.Generic";

    /// How long one call may take. A service started by D-Bus activation on
    /// the first call answers in well under this.
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    /// How long the user has to answer an unlock prompt.
    public static final Duration PROMPT_TIMEOUT = Duration.ofMinutes(2);

    private final Supplier<Optional<Dbus>> connect;
    private final Duration promptTimeout;

    private SecretService(Supplier<Optional<Dbus>> connect, Duration promptTimeout) {
        this.connect = connect;
        this.promptTimeout = promptTimeout;
    }

    /// The Secret Service on the session bus, or empty where there is no
    /// libdbus. Whether a service is running is found out on the first call.
    public static Optional<SecretService> connect() {
        return Dbus.isAvailable() ? Optional.of(new SecretService(Dbus::session, PROMPT_TIMEOUT)) : Optional.empty();
    }

    /// The Secret Service on the bus at `address`, for a test that runs a bus
    /// of its own.
    static SecretService at(String address, Duration promptTimeout) {
        return new SecretService(() -> Dbus.at(address), promptTimeout);
    }

    @Override
    public String name() {
        return "Secret Service";
    }

    @Override
    public void store(String service, String account, String label, byte[] secret, String contentType) {
        try (var bus = open()) {
            var collection = defaultCollection(bus);
            unlock(bus, List.of(collection));
            var session = openSession(bus);
            try {
                var created = call(bus, collection.value(), COLLECTION, "CreateItem", arguments -> {
                    arguments.array("{sv}", properties -> {
                        properties.entry(LABEL, "s", value -> value.string(label));
                        properties.entry(
                                ATTRIBUTES,
                                "a{ss}",
                                value -> value.array(
                                        "{ss}",
                                        map -> attributes(service, account, true)
                                                .forEach(map::entry)));
                    });
                    arguments.struct(struct -> struct.objectPath(session.value())
                            .bytes(new byte[0])
                            .bytes(secret)
                            .string(contentType));
                    arguments.bool(true);
                });
                prompt(bus, path(created, 1));
            } finally {
                closeSession(bus, session);
            }
        }
    }

    @Override
    public Optional<byte[]> load(String service, String account) {
        try (var bus = open()) {
            var items = find(bus, service, account, false);
            if (items.isEmpty()) {
                return Optional.empty();
            }
            var session = openSession(bus);
            try {
                var answer = call(
                        bus,
                        items.getFirst().value(),
                        ITEM,
                        "GetSecret",
                        arguments -> arguments.objectPath(session.value()));
                if (!answer.isEmpty()
                        && answer.getFirst() instanceof Dbus.Struct(var fields)
                        && fields.size() == 4
                        && fields.get(2) instanceof byte[] value) {
                    return Optional.of(value);
                }
                throw new SecretStoreException(Reason.FAILED, "the Secret Service answered GetSecret with " + answer);
            } finally {
                closeSession(bus, session);
            }
        }
    }

    @Override
    public boolean delete(String service, String account) {
        try (var bus = open()) {
            var items = find(bus, service, account, true);
            for (var item : items) {
                prompt(bus, path(call(bus, item.value(), ITEM, "Delete", _ -> {}), 0));
            }
            return !items.isEmpty();
        }
    }

    @Override
    public void close() {
        // A connection is opened per call and closed after it.
    }

    /// The attributes an item is stored and found by. The schema is written
    /// and not searched for, so an item another library stored without it is
    /// still found.
    static SequencedMap<String, String> attributes(String service, String account, boolean withSchema) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("service", service);
        attributes.put("username", account);
        if (withSchema) {
            attributes.put("xdg:schema", SCHEMA);
        }
        return attributes;
    }

    private Dbus open() {
        return connect.get()
                .orElseThrow(() -> new SecretStoreException(Reason.UNAVAILABLE, "there is no session bus to ask"));
    }

    /// The unlocked items for `service` and `account`, unlocking the locked
    /// ones if that is all there is, or if `all` of them are wanted.
    private List<ObjectPath> find(Dbus bus, String service, String account, boolean all) {
        var found = call(
                bus,
                PATH,
                SERVICE,
                "SearchItems",
                arguments -> arguments.array(
                        "{ss}", map -> attributes(service, account, false).forEach(map::entry)));
        var unlocked = paths(found, 0);
        var locked = paths(found, 1);
        if (locked.isEmpty() || !all && !unlocked.isEmpty()) {
            return unlocked;
        }
        var items = new ArrayList<>(unlocked);
        items.addAll(unlock(bus, locked));
        return items;
    }

    private ObjectPath defaultCollection(Dbus bus) {
        var alias = path(call(bus, PATH, SERVICE, "ReadAlias", arguments -> arguments.string("default")), 0);
        if (alias.isNone()) {
            throw new SecretStoreException(Reason.UNAVAILABLE, "the Secret Service has no default keyring");
        }
        return alias;
    }

    /// Unlocks `objects`, through the service's prompt if it asks for one, and
    /// answers the ones that are unlocked now.
    private List<ObjectPath> unlock(Dbus bus, List<ObjectPath> objects) {
        var answer = call(
                bus,
                PATH,
                SERVICE,
                "Unlock",
                arguments -> arguments.array("o", array -> objects.forEach(o -> array.objectPath(o.value()))));
        var unlocked = paths(answer, 0);
        var completed = prompt(bus, path(answer, 1));
        if (completed instanceof List<?> more) {
            var all = new ArrayList<>(unlocked);
            more.stream()
                    .filter(ObjectPath.class::isInstance)
                    .map(ObjectPath.class::cast)
                    .forEach(all::add);
            unlocked = all;
        }
        if (unlocked.isEmpty()) {
            throw new SecretStoreException(Reason.DENIED, "the keyring stayed locked");
        }
        return unlocked;
    }

    /// Runs the prompt at `prompt` and answers its result, or null where there
    /// was nothing to prompt for.
    private @Nullable Object prompt(Dbus bus, ObjectPath prompt) {
        if (prompt.isNone()) {
            return null;
        }
        // Before the call, so the signal that answers it cannot be missed.
        bus.match("type='signal',interface='" + PROMPT + "',member='Completed',path='" + prompt.value() + "'");
        call(bus, prompt.value(), PROMPT, "Prompt", arguments -> arguments.string(""));
        var completed = bus.awaitSignal(PROMPT, "Completed", prompt.value(), promptTimeout);
        if (completed.isEmpty()) {
            bus.invoke(NAME, prompt.value(), PROMPT, "Dismiss", _ -> {}, CALL_TIMEOUT);
            throw new SecretStoreException(
                    Reason.DENIED, "nobody answered the keyring's prompt in " + promptTimeout.toSeconds() + " s");
        }
        var arguments = completed.get();
        if (arguments.isEmpty() || !(arguments.getFirst() instanceof Boolean dismissed) || dismissed) {
            throw new SecretStoreException(Reason.DENIED, "the keyring's prompt was dismissed");
        }
        return arguments.size() > 1 && arguments.get(1) instanceof Dbus.Variant(var result) ? result : null;
    }

    private ObjectPath openSession(Dbus bus) {
        return path(
                call(
                        bus,
                        PATH,
                        SERVICE,
                        "OpenSession",
                        arguments -> arguments.string("plain").variant("s", value -> value.string(""))),
                1);
    }

    private static void closeSession(Dbus bus, ObjectPath session) {
        bus.invoke(NAME, session.value(), SESSION, "Close", _ -> {}, CALL_TIMEOUT);
    }

    private static List<Object> call(
            Dbus bus, String path, String iface, String member, Consumer<Dbus.Writer> arguments) {
        return switch (bus.invoke(NAME, path, iface, member, arguments, CALL_TIMEOUT)) {
            case Reply.Returned(var values) -> values;
            case Reply.Failed(var name, var message) -> throw failure(member, name, message);
            case Reply.Unanswered() ->
                throw new SecretStoreException(Reason.UNAVAILABLE, "the Secret Service did not answer " + member);
        };
    }

    static SecretStoreException failure(String member, String name, String message) {
        var reason = switch (name) {
            case "org.freedesktop.DBus.Error.ServiceUnknown",
                    "org.freedesktop.DBus.Error.NameHasNoOwner",
                    "org.freedesktop.DBus.Error.Spawn.ChildExited",
                    "org.freedesktop.DBus.Error.Spawn.ServiceNotFound" -> Reason.UNAVAILABLE;
            case "org.freedesktop.Secret.Error.IsLocked", "org.freedesktop.DBus.Error.AccessDenied" -> Reason.DENIED;
            default -> Reason.FAILED;
        };
        var text = reason == Reason.UNAVAILABLE
                ? "no Secret Service on the session bus (gnome-keyring, KWallet or KeePassXC serve one)"
                : member + " failed: " + name + (message.isEmpty() ? "" : " (" + message + ")");
        return new SecretStoreException(reason, text);
    }

    private static ObjectPath path(List<Object> values, int index) {
        if (values.size() > index && values.get(index) instanceof ObjectPath path) {
            return path;
        }
        throw new SecretStoreException(Reason.FAILED, "the Secret Service answered " + values);
    }

    private static List<ObjectPath> paths(List<Object> values, int index) {
        if (values.size() > index && values.get(index) instanceof List<?> list) {
            return list.stream()
                    .filter(ObjectPath.class::isInstance)
                    .map(ObjectPath.class::cast)
                    .toList();
        }
        throw new SecretStoreException(Reason.FAILED, "the Secret Service answered " + values);
    }
}
