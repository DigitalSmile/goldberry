package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Debug groups and labels in a command buffer: what a frame capture in Xcode,
/// RenderDoc or PIX names each layer's commands by (`docs/gpu-plan.md`,
/// phase 2). They cost nothing a driver without a debugger attached notices.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuDebugCalls(
        PushGPUDebugGroup pushGPUDebugGroup,
        PopGPUDebugGroup popGPUDebugGroup,
        InsertGPUDebugLabel insertGPUDebugLabel) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuDebugCalls bind(SymbolLookup lookup) {
        return new SdlGpuDebugCalls(
                new PushGPUDebugGroup(lookup), new PopGPUDebugGroup(lookup), new InsertGPUDebugLabel(lookup));
    }

    /// Begins a named group of commands.
    ///
    /// `void SDL_PushGPUDebugGroup(void*, const char*)`
    public static final class PushGPUDebugGroup {

        private static final MethodHandle FD_SDL_PushGPUDebugGroup =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        PushGPUDebugGroup(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PushGPUDebugGroup");
        }

        /// Calls `SDL_PushGPUDebugGroup`.
        ///
        /// @param commandBuffer the command buffer
        /// @param name          a UTF-8 C string
        public void call(MemorySegment commandBuffer, MemorySegment name) {
            try {
                FD_SDL_PushGPUDebugGroup.invokeExact(address, commandBuffer, name);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PushGPUDebugGroup", t);
            }
        }
    }

    /// Ends the group most recently begun.
    ///
    /// `void SDL_PopGPUDebugGroup(void*)`
    public static final class PopGPUDebugGroup {

        private static final MethodHandle FD_SDL_PopGPUDebugGroup = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        PopGPUDebugGroup(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PopGPUDebugGroup");
        }

        /// Calls `SDL_PopGPUDebugGroup`.
        ///
        /// @param commandBuffer the command buffer
        public void call(MemorySegment commandBuffer) {
            try {
                FD_SDL_PopGPUDebugGroup.invokeExact(address, commandBuffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PopGPUDebugGroup", t);
            }
        }
    }

    /// Marks a point in the command stream.
    ///
    /// `void SDL_InsertGPUDebugLabel(void*, const char*)`
    public static final class InsertGPUDebugLabel {

        private static final MethodHandle FD_SDL_InsertGPUDebugLabel =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        InsertGPUDebugLabel(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_InsertGPUDebugLabel");
        }

        /// Calls `SDL_InsertGPUDebugLabel`.
        ///
        /// @param commandBuffer the command buffer
        /// @param text          a UTF-8 C string
        public void call(MemorySegment commandBuffer, MemorySegment text) {
            try {
                FD_SDL_InsertGPUDebugLabel.invokeExact(address, commandBuffer, text);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_InsertGPUDebugLabel", t);
            }
        }
    }
}
