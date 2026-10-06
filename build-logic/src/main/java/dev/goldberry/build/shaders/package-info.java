/**
 * What {@code :gpu:compileShaders} knows about a shader beyond compiling it: the
 * Metal binding each HLSL resource must land on, which SDL_GPU fixes by order
 * and SPIRV-Cross cannot work out from the SPIR-V alone.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/components/gpu.html#the-renderer">The
 * renderer</a>.
 */
package dev.goldberry.build.shaders;
