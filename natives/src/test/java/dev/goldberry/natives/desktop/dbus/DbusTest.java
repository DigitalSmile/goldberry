package dev.goldberry.natives.desktop.dbus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.natives.NativePlatform;

/// What [Dbus] writes comes back as the values it reads: an echo service on a
/// bus of the test's own, which answers every call with its own arguments.
///
/// Skipped where there is no `dbus-daemon` or no libdbus.
@DisplayName("a D-Bus connection")
@Timeout(30)
class DbusTest {

    private static final String NAME = "dev.goldberry.Echo";
    private static final String PATH = "/dev/goldberry/Echo";

    @TempDir
    Path directory;

    private PrivateBus bus;
    private Dbus client;

    @BeforeEach
    void start() throws Exception {
        assumeTrue(NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX, "D-Bus is Linux's");
        bus = PrivateBus.start(directory);
        assumeTrue(bus != null, "no dbus-daemon or no libdbus here");
        assertTrue(bus.serve(NAME, _ -> {}, DbusTest::echo));
        client = bus.connect();
    }

    @AfterEach
    void stop() {
        if (client != null) {
            client.close();
        }
        if (bus != null) {
            bus.close();
        }
    }

    /// Answers `Echo` with the arguments it was given, written back the way a
    /// Secret Service writes a secret: `(oayays)`. `Fail` is an error reply,
    /// and `Ping` emits a signal before answering.
    private static void echo(Dbus.Incoming call) {
        if (!call.isCall()) {
            return;
        }
        switch (call.member()) {
            case "Echo" -> {
                var secret = (Dbus.Struct) call.arguments().getFirst();
                var fields = secret.fields();
                call.reply(reply -> reply.struct(struct -> struct.objectPath(((Dbus.ObjectPath) fields.get(0)).value())
                                .bytes((byte[]) fields.get(1))
                                .bytes((byte[]) fields.get(2))
                                .string((String) fields.get(3)))
                        .array("{ss}", attributes -> attributes.entry("service", "chat"))
                        .variant(
                                "ao",
                                paths -> paths.array(
                                        "o", array -> array.objectPath("/a").objectPath("/b"))));
            }
            case "Fail" -> call.fail("dev.goldberry.Error.Refused", "not today");
            default -> {
                // Left unanswered, for the caller to time out on.
            }
        }
    }

    @Test
    @DisplayName("reads back structs, byte arrays, dictionaries, variants and object paths")
    void roundTrip() {
        var secret = new byte[] {0, 1, (byte) 0xFF, 42};

        var reply = client.invoke(
                NAME,
                PATH,
                NAME,
                "Echo",
                arguments -> arguments.struct(struct -> struct.objectPath("/session/1")
                        .bytes(new byte[0])
                        .bytes(secret)
                        .string("application/octet-stream")),
                Duration.ofSeconds(5));

        var values = assertInstanceOf(Dbus.Reply.Returned.class, reply).values();
        assertEquals(3, values.size(), values.toString());
        var fields = assertInstanceOf(Dbus.Struct.class, values.get(0)).fields();
        assertEquals(new Dbus.ObjectPath("/session/1"), fields.get(0));
        assertArrayEquals(new byte[0], (byte[]) fields.get(1), "an empty byte array is still one");
        assertArrayEquals(secret, (byte[]) fields.get(2));
        assertEquals("application/octet-stream", fields.get(3));
        assertEquals(List.of(new Dbus.Entry("service", "chat")), values.get(1));
        assertEquals(new Dbus.Variant(List.of(new Dbus.ObjectPath("/a"), new Dbus.ObjectPath("/b"))), values.get(2));
    }

    @Test
    @DisplayName("tells an error reply from no reply, by the error's name")
    void errors() {
        var refused = client.invoke(NAME, PATH, NAME, "Fail", _ -> {}, Duration.ofSeconds(5));
        var nobody = client.invoke("dev.goldberry.Nobody", PATH, NAME, "Echo", _ -> {}, Duration.ofSeconds(5));
        var silent = client.invoke(NAME, PATH, NAME, "Silence", _ -> {}, Duration.ofMillis(200));

        assertEquals(new Dbus.Reply.Failed("dev.goldberry.Error.Refused", "not today"), refused);
        assertEquals(
                "org.freedesktop.DBus.Error.ServiceUnknown",
                assertInstanceOf(Dbus.Reply.Failed.class, nobody).name());
        assertEquals(new Dbus.Reply.Unanswered(), silent);
    }

    @Test
    @DisplayName("waits for one signal at one path and passes over the others")
    void awaitsASignal() {
        client.match("type='signal',interface='dev.goldberry.Prompt'");
        var service = bus.connect();
        try {
            service.signal("/prompt/other", "dev.goldberry.Prompt", "Completed", signal -> signal.bool(true));
            service.signal(
                    "/prompt/1",
                    "dev.goldberry.Prompt",
                    "Completed",
                    signal -> signal.bool(false).variant("s", value -> value.string("done")));

            var arguments = client.awaitSignal("dev.goldberry.Prompt", "Completed", "/prompt/1", Duration.ofSeconds(5));

            assertEquals(List.of(false, new Dbus.Variant("done")), arguments.orElseThrow());
            assertTrue(client.awaitSignal("dev.goldberry.Prompt", "Completed", "/prompt/2", Duration.ofMillis(100))
                    .isEmpty());
        } finally {
            service.close();
        }
    }
}
