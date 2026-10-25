package dev.goldberry.gpu.raytrace;

import java.util.Objects;

/// Whether rays can be traced here, and on what: the answer a game's options
/// screen shows beside its ray-tracing switch.
///
/// Asked with [RayTracer#availability()], which looks once a process.
public sealed interface RayTracing {

    /// Whether rays can be traced.
    default boolean isAvailable() {
        return this instanceof Hardware;
    }

    /// No ray tracing, and why: no Vulkan, no device with the ray-tracing
    /// extensions (each device is named with what it lacks), or switched off.
    ///
    /// @param reason a sentence for a person
    record Unavailable(String reason) implements RayTracing {

        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /// Rays traced in hardware, through Vulkan's ray-tracing pipelines, on a
    /// device of the toolkit's own beside SDL's.
    ///
    /// @param device the device's name, as its driver gives it
    record Hardware(String device) implements RayTracing {

        public Hardware {
            Objects.requireNonNull(device, "device");
        }
    }
}
