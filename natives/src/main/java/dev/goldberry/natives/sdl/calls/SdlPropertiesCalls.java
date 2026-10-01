package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// SDL's property groups: the options a call takes by name rather than by
/// field, which is how `SDL_CreateGPUDeviceWithProperties` is configured.
///
/// Only the setters and the lifecycle: the three getters a window's native handle
/// is read through are in [SdlWindowCalls], beside the window they read.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlPropertiesCalls(
        CreateProperties createProperties,
        DestroyProperties destroyProperties,
        SetBooleanProperty setBooleanProperty,
        SetStringProperty setStringProperty) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlPropertiesCalls bind(SymbolLookup lookup) {
        return new SdlPropertiesCalls(
                new CreateProperties(lookup),
                new DestroyProperties(lookup),
                new SetBooleanProperty(lookup),
                new SetStringProperty(lookup));
    }

    /// Creates an empty property group, which is how SDL takes options that have
    /// no field in a struct: a GPU device's driver, debug mode and shader formats.
    ///
    /// `uint32_t SDL_CreateProperties()`
    public static final class CreateProperties {

        private static final MethodHandle FD_SDL_CreateProperties = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        CreateProperties(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateProperties");
        }

        /// Calls `SDL_CreateProperties`.
        ///
        /// @return the group's id, or 0 on failure
        public int call() {
            try {
                return (int) FD_SDL_CreateProperties.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateProperties", t);
            }
        }
    }

    /// Destroys a property group, and every value set in it.
    ///
    /// `void SDL_DestroyProperties(uint32_t)`
    public static final class DestroyProperties {

        private static final MethodHandle FD_SDL_DestroyProperties =
                Downcalls.link(FunctionDescriptor.ofVoid(JAVA_INT));

        private final MemorySegment address;

        DestroyProperties(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyProperties");
        }

        /// Calls `SDL_DestroyProperties`.
        ///
        /// @param props the group to destroy
        public void call(int props) {
            try {
                FD_SDL_DestroyProperties.invokeExact(address, props);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyProperties", t);
            }
        }
    }

    /// Sets a boolean property.
    ///
    /// `_Bool SDL_SetBooleanProperty(uint32_t, void*, _Bool)`
    public static final class SetBooleanProperty {

        private static final MethodHandle FD_SDL_SetBooleanProperty =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        SetBooleanProperty(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetBooleanProperty");
        }

        /// Calls `SDL_SetBooleanProperty`.
        ///
        /// @param props the group
        /// @param name  the property's name, a C string
        /// @param value its value
        /// @return false if SDL refused
        public boolean call(int props, MemorySegment name, boolean value) {
            try {
                return (boolean) FD_SDL_SetBooleanProperty.invokeExact(address, props, name, value);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetBooleanProperty", t);
            }
        }
    }

    /// Sets a string property. SDL copies the value.
    ///
    /// `_Bool SDL_SetStringProperty(uint32_t, void*, void*)`
    public static final class SetStringProperty {

        private static final MethodHandle FD_SDL_SetStringProperty =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetStringProperty(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetStringProperty");
        }

        /// Calls `SDL_SetStringProperty`.
        ///
        /// @param props the group
        /// @param name  the property's name, a C string
        /// @param value its value, a C string SDL copies
        /// @return false if SDL refused
        public boolean call(int props, MemorySegment name, MemorySegment value) {
            try {
                return (boolean) FD_SDL_SetStringProperty.invokeExact(address, props, name, value);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetStringProperty", t);
            }
        }
    }
}
