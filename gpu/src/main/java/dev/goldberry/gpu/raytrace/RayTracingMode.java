package dev.goldberry.gpu.raytrace;

import java.util.Locale;

/// What an application asks of ray tracing: the setting behind a game's
/// ray-tracing switch.
public enum RayTracingMode {
    /// No ray tracing, whatever the device can do.
    OFF,
    /// Ray tracing where the device has it, and none where it does not.
    AUTO,
    /// Ray tracing in hardware, or a refusal that says why there is none.
    HARDWARE;

    /// `goldberry.raytracing`, which overrides what an application asks: `off`
    /// turns ray tracing off for the process, and `auto` or `hardware` is
    /// the mode it names.
    public static final String PROPERTY = "goldberry.raytracing";

    /// The mode `value` names, ignoring case: `off`, `auto` or `hardware`.
    ///
    /// @throws IllegalArgumentException when it names none
    public static RayTracingMode parse(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "off" -> OFF;
            case "auto" -> AUTO;
            case "hardware" -> HARDWARE;
            default -> throw new IllegalArgumentException(PROPERTY + " is off, auto or hardware, not '" + value + "'");
        };
    }

    /// The mode in force when an application asks for `asked`: the
    /// property's, when it is set, and `asked` otherwise.
    ///
    /// @throws IllegalArgumentException when the property names no mode
    public static RayTracingMode effective(RayTracingMode asked) {
        var property = System.getProperty(PROPERTY);
        return property == null || property.isBlank() ? asked : parse(property);
    }
}
