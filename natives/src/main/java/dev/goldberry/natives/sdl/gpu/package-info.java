/// SDL's GPU API, wrapped: a device, its textures and transfer buffers, command
/// buffers with their passes, and fences.
///
/// Exported to `:core`, which claims windows and presents through it, and to
/// `:gpu`, whose public API is built on it. Nothing here
/// has a `MemorySegment` in a public signature: handles stay inside the wrapper
/// that owns them, and mapped memory leaves as a [java.nio.ByteBuffer] that
/// stops working, rather than crashing, once it is unmapped.
///
/// A device needs SDL's video subsystem, under a video driver that can make a
/// Metal view or a Vulkan surface. SDL's `dummy` driver can make neither, so the
/// headless tests have no device under it; `offscreen` has Vulkan, which is what
/// the GPU lane runs lavapipe under.
///
/// Nothing here is thread-safe. A device and everything made from it belong to
/// the thread that uses them, which in the toolkit is the UI thread.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.sdl.gpu;
