package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuBufferCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuCommandCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuDebugCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuDeviceCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuPipelineCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuRenderPassCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuResourceCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuSwapchainCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlPropertiesCalls;

/// The holder records this package calls through, bound once, the first
/// time anything here is used: the lazy holder idiom every binding class uses.
///
/// @param device     device creation and queries
/// @param resources  textures and transfer buffers
/// @param commands   command buffers, passes, blits and fences
/// @param swapchain  claimed windows and their swapchains
/// @param pipelines  shaders, samplers and graphics pipelines
/// @param renderPass what a render pass records
/// @param buffers    vertex and index buffers, their copies, and indexed draws
/// @param debug      debug groups and labels
/// @param properties the property groups a device is configured with
record GpuCalls(
        SdlGpuDeviceCalls device,
        SdlGpuResourceCalls resources,
        SdlGpuCommandCalls commands,
        SdlGpuSwapchainCalls swapchain,
        SdlGpuPipelineCalls pipelines,
        SdlGpuRenderPassCalls renderPass,
        SdlGpuBufferCalls buffers,
        SdlGpuDebugCalls debug,
        SdlPropertiesCalls properties) {

    /// The bound calls.
    static GpuCalls get() {
        return Holder.CALLS;
    }

    private static final class Holder {
        private static final GpuCalls CALLS = bind();

        private static GpuCalls bind() {
            var lookup = NativeLibrary.get().lookup();
            return new GpuCalls(
                    SdlGpuDeviceCalls.bind(lookup),
                    SdlGpuResourceCalls.bind(lookup),
                    SdlGpuCommandCalls.bind(lookup),
                    SdlGpuSwapchainCalls.bind(lookup),
                    SdlGpuPipelineCalls.bind(lookup),
                    SdlGpuRenderPassCalls.bind(lookup),
                    SdlGpuBufferCalls.bind(lookup),
                    SdlGpuDebugCalls.bind(lookup),
                    SdlPropertiesCalls.bind(lookup));
        }
    }
}
