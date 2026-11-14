package dev.goldberry.natives.desktop.keychain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.dbus.Dbus;
import dev.goldberry.natives.desktop.dbus.PrivateBus;

/// A Secret Service of the test's own, on a bus of the test's own (see
/// [PrivateBus]), so the keyring of whoever runs the suite is never touched.
///
/// It keeps items in memory in one collection, the default one, which can be
/// locked. Unlocking goes through a prompt, which the test decides the user
/// accepts, dismisses or never answers. Everything else is the specification's
/// happy path: one algorithm (`plain`), one alias (`default`).
final class FakeSecretService implements AutoCloseable {

    static final String COLLECTION_PATH = "/org/freedesktop/secrets/collection/login";

    /// What the user does with an unlock prompt.
    enum Answer {
        ACCEPT,
        DISMISS,
        IGNORE
    }

    /// One stored item.
    record Item(String label, Map<String, String> attributes, byte[] secret, String contentType) {}

    final Map<String, Item> items = new ConcurrentHashMap<>();
    final AtomicInteger sessionsOpened = new AtomicInteger();
    final AtomicInteger sessionsClosed = new AtomicInteger();
    final AtomicInteger promptsShown = new AtomicInteger();
    final AtomicInteger promptsDismissedByCaller = new AtomicInteger();

    volatile boolean locked;
    volatile Answer answer = Answer.ACCEPT;

    private final PrivateBus bus;
    private final AtomicInteger next = new AtomicInteger();
    private final Map<String, List<Dbus.ObjectPath>> prompts = new ConcurrentHashMap<>();

    private FakeSecretService(PrivateBus bus) {
        this.bus = bus;
    }

    /// A bus with the service on it, or null where this machine has no
    /// `dbus-daemon` or no libdbus.
    static @Nullable FakeSecretService start(Path directory) throws IOException {
        var bus = PrivateBus.start(directory);
        if (bus == null) {
            return null;
        }
        var service = new FakeSecretService(bus);
        if (!bus.serve(SecretService.NAME, _ -> {}, service::handle)) {
            bus.close();
            return null;
        }
        return service;
    }

    String address() {
        return bus.address();
    }

    /// Puts an item in, as another application would have.
    void put(Map<String, String> attributes, byte[] secret) {
        items.put(COLLECTION_PATH + "/" + next.incrementAndGet(), new Item("put", attributes, secret, "text/plain"));
    }

    private void handle(Dbus.Incoming call) {
        if (!call.isCall()) {
            return;
        }
        var arguments = call.arguments();
        switch (call.iface() + "." + call.member()) {
            case SecretService.SERVICE + ".OpenSession" -> {
                sessionsOpened.incrementAndGet();
                var session = "/org/freedesktop/secrets/session/" + next.incrementAndGet();
                call.reply(
                        reply -> reply.variant("s", value -> value.string("")).objectPath(session));
            }
            case SecretService.SESSION + ".Close" -> {
                sessionsClosed.incrementAndGet();
                call.reply(_ -> {});
            }
            case SecretService.SERVICE + ".ReadAlias" ->
                call.reply(reply -> reply.objectPath("default".equals(arguments.getFirst()) ? COLLECTION_PATH : "/"));
            case SecretService.SERVICE + ".SearchItems" -> {
                var wanted = map(arguments.getFirst());
                var found = items.entrySet().stream()
                        .filter(item -> item.getValue().attributes().entrySet().containsAll(wanted.entrySet()))
                        .map(Map.Entry::getKey)
                        .toList();
                call.reply(reply -> reply.array("o", unlocked -> {
                            if (!locked) {
                                found.forEach(unlocked::objectPath);
                            }
                        })
                        .array("o", lockedOnes -> {
                            if (locked) {
                                found.forEach(lockedOnes::objectPath);
                            }
                        }));
            }
            case SecretService.SERVICE + ".Unlock" -> {
                var objects = ((List<?>) arguments.getFirst())
                        .stream().map(Dbus.ObjectPath.class::cast).toList();
                if (!locked) {
                    call.reply(reply -> reply.array("o", array -> objects.forEach(o -> array.objectPath(o.value())))
                            .objectPath("/"));
                } else {
                    var prompt = "/org/freedesktop/secrets/prompt/" + next.incrementAndGet();
                    prompts.put(prompt, objects);
                    call.reply(reply -> reply.array("o", _ -> {}).objectPath(prompt));
                }
            }
            case SecretService.PROMPT + ".Prompt" -> {
                promptsShown.incrementAndGet();
                call.reply(_ -> {});
                var objects = prompts.remove(call.path());
                switch (answer) {
                    case ACCEPT -> {
                        locked = false;
                        completed(call.path(), false, objects == null ? List.of() : objects);
                    }
                    case DISMISS -> completed(call.path(), true, List.of());
                    case IGNORE -> {
                        // The user walked away.
                    }
                }
            }
            case SecretService.PROMPT + ".Dismiss" -> {
                promptsDismissedByCaller.incrementAndGet();
                call.reply(_ -> {});
            }
            case SecretService.COLLECTION + ".CreateItem" -> createItem(call, arguments);
            case SecretService.ITEM + ".GetSecret" -> {
                var item = items.get(call.path());
                if (item == null) {
                    call.fail("org.freedesktop.Secret.Error.NoSuchObject", "no such item");
                } else if (locked) {
                    call.fail("org.freedesktop.Secret.Error.IsLocked", "locked");
                } else {
                    var session = ((Dbus.ObjectPath) arguments.getFirst()).value();
                    call.reply(reply -> reply.struct(struct -> struct.objectPath(session)
                            .bytes(new byte[0])
                            .bytes(item.secret())
                            .string(item.contentType())));
                }
            }
            case SecretService.ITEM + ".Delete" -> {
                if (locked) {
                    call.fail("org.freedesktop.Secret.Error.IsLocked", "locked");
                } else {
                    items.remove(call.path());
                    call.reply(reply -> reply.objectPath("/"));
                }
            }
            default -> call.fail("org.freedesktop.DBus.Error.UnknownMethod", call.iface() + "." + call.member());
        }
    }

    private void createItem(Dbus.Incoming call, List<Object> arguments) {
        if (locked) {
            call.fail("org.freedesktop.Secret.Error.IsLocked", "locked");
            return;
        }
        var label = "";
        Map<String, String> attributes = Map.of();
        for (var property : (List<?>) arguments.get(0)) {
            var entry = (Dbus.Entry) property;
            var value = ((Dbus.Variant) entry.value()).value();
            switch ((String) entry.key()) {
                case SecretService.LABEL -> label = (String) value;
                case SecretService.ATTRIBUTES -> attributes = map(value);
                default -> {
                    // Not kept.
                }
            }
        }
        var secret = ((Dbus.Struct) arguments.get(1)).fields();
        var replace = (Boolean) arguments.get(2);
        var same = attributes;
        var path = replace
                ? items.entrySet().stream()
                        .filter(item -> item.getValue().attributes().equals(same))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null)
                : null;
        var stored = path == null ? COLLECTION_PATH + "/" + next.incrementAndGet() : path;
        items.put(stored, new Item(label, attributes, (byte[]) secret.get(2), (String) secret.get(3)));
        call.reply(reply -> reply.objectPath(stored).objectPath("/"));
    }

    private void completed(String prompt, boolean dismissed, List<Dbus.ObjectPath> result) {
        bus.service()
                .signal(
                        prompt,
                        SecretService.PROMPT,
                        "Completed",
                        signal -> signal.bool(dismissed)
                                .variant(
                                        "ao",
                                        value -> value.array(
                                                "o", array -> result.forEach(o -> array.objectPath(o.value())))));
    }

    private static Map<String, String> map(Object dictionary) {
        var map = new LinkedHashMap<String, String>();
        for (var entry : (List<?>) dictionary) {
            var pair = (Dbus.Entry) entry;
            map.put((String) pair.key(), (String) pair.value());
        }
        return map;
    }

    /// The paths of the items, sorted, for an assertion.
    List<String> paths() {
        var paths = new ArrayList<>(items.keySet());
        paths.sort(null);
        return paths;
    }

    @Override
    public void close() {
        bus.close();
    }
}
