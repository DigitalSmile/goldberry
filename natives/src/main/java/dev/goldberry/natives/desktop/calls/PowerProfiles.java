package dev.goldberry.natives.desktop.calls;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.dbus.Dbus;

/// Whether a Linux desktop is in its power-saver profile: power-profiles-daemon's
/// `ActiveProfile`, over the system bus.
///
/// The daemon is what GNOME's and KDE's power menus set, under the name it has
/// had since 0.20 and under the one before it. A machine without it, without a
/// system bus or without libdbus answers empty, which is "the desktop does not
/// say" rather than "off".
public final class PowerProfiles {

    /// The system bus, where the daemon lives.
    private static final String SYSTEM_BUS = "unix:path=/var/run/dbus/system_bus_socket";

    /// The daemon's names, newest first.
    private static final List<String[]> NAMES = List.of(
            new String[] {"org.freedesktop.UPower.PowerProfiles", "/org/freedesktop/UPower/PowerProfiles"},
            new String[] {"net.hadess.PowerProfiles", "/net/hadess/PowerProfiles"});

    /// Short: this is asked from the UI thread every so often, and a wedged bus
    /// must cost little.
    private static final Duration TIMEOUT = Duration.ofMillis(150);

    private PowerProfiles() {}

    /// Whether the active profile is `power-saver`, or empty where nothing says.
    public static Optional<Boolean> read() {
        if (!Dbus.isAvailable()) {
            return Optional.empty();
        }
        try (var bus = Dbus.at(SYSTEM_BUS).orElse(null)) {
            if (bus == null) {
                return Optional.empty();
            }
            for (var name : NAMES) {
                var reply = bus.invoke(
                        name[0],
                        name[1],
                        "org.freedesktop.DBus.Properties",
                        "Get",
                        writer -> writer.string(name[0]).string("ActiveProfile"),
                        TIMEOUT);
                if (reply instanceof Dbus.Reply.Returned(var values) && profile(values) instanceof String profile) {
                    return Optional.of(profile.equals("power-saver"));
                }
            }
            return Optional.empty();
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            return Optional.empty();
        }
    }

    /// The string inside however many variants the reply wraps it in, or null.
    static @Nullable Object profile(List<Object> values) {
        if (values.isEmpty()) {
            return null;
        }
        var value = values.getFirst();
        while (value instanceof Dbus.Variant(var inner)) {
            value = inner;
        }
        return value instanceof String ? value : null;
    }
}
