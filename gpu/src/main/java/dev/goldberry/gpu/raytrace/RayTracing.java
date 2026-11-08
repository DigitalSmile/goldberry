package dev.goldberry.gpu.raytrace;

import java.util.Objects;

/// Whether rays can be traced here, and on what: the answer a game's options
/// screen shows beside its ray-tracing switch.
///
/// Asked with [RayTracer#availability()], which answers for the hardware tier
/// and looks once a process, or [RayTracer#availability(dev.goldberry.gpu.GpuDevice, RayTracingMode)],
/// which answers for whichever tier [RayTracer#open(dev.goldberry.gpu.GpuDevice, RayTracingMode)]
/// would open.
public sealed interface RayTracing {

    /// Whether rays can be traced, by either tier.
    default boolean isAvailable() {
        return this instanceof Available;
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

    /// Rays can be traced, and this is how: a tier, and the device it traces
    /// on.
    sealed interface Available extends RayTracing {

        /// The device's name, as its driver gives it.
        String device();
    }

    /// Rays traced in hardware, through Vulkan's ray-tracing pipelines, on a
    /// device of the toolkit's own beside SDL's.
    ///
    /// @param device the device's name, as its driver gives it
    record Hardware(String device) implements Available {

        public Hardware {
            Objects.requireNonNull(device, "device");
        }
    }

    /// Rays traced in software: compute shaders walking a hierarchy of boxes
    /// built on the CPU, on the window's own device, on any GPU it has.
    ///
    /// @param device the device's name, as its driver gives it
    record Software(String device) implements Available {

        public Software {
            Objects.requireNonNull(device, "device");
        }
    }
}
