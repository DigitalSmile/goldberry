# ADR-0568: A Metal shader is bound in SDL's order, and the registers say what that is

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:**
  [ADR-0565](0565-a-compute-pass-names-what-it-writes-and-a-storage-usage-is-per-stage.md),
  `gpu/build.gradle`, `build-logic/.../shaders/MetalBindings.java`

## Context

`:gpu:compileShaders` compiles each HLSL shader three ways: DXC to DXIL for
Direct3D 12, DXC to SPIR-V for Vulkan, and SPIRV-Cross from that SPIR-V to MSL
for Metal. The HLSL registers follow SDL_GPU's conventions, which name a
descriptor set per resource class: for a compute shader, read-only storage
resources in set 0, read-write ones in set 1, uniform buffers in set 2.

SDL_GPU binds a Metal shader by order, not by set. In a `[[buffer]]` slot the
uniform buffers come first, then the read-only storage buffers, then the
read-write ones; a `[[texture]]` slot holds the sampled textures, then the
read-only storage textures, then the read-write ones; a sampler takes its
texture's index. SPIRV-Cross, given no bindings of its own, numbers each slot
kind in descriptor-set order. For every shader that shipped before the compute
pass that was the same order, because no stage had both a uniform and a
storage buffer. The first compute shader had both, and on Metal it read the
uniform where SDL had bound the input and wrote nothing where SDL expected the
output. The macOS lane failed on a test that passed on lavapipe and would have
passed on Direct3D 12.

## Decision

The build works out each resource's Metal index from the registers and hands
it to SPIRV-Cross as the SPIR-V binding. `MetalBindings`, in build-logic, reads
the shader's declarations (and its `#include`s) and computes SDL's order:
uniforms by their `b` number; storage buffers in `t` registers after the
uniforms, numbered past the textures that share the `t` registers; read-write
storage buffers in `u` registers after those; textures by their `t` number,
read-write textures after them; samplers by their `s` number. The task runs
DXC a second time with one `-fvk-bind-register` per resource, textures and
samplers in one descriptor set and buffers in another, and SPIRV-Cross with
`--msl-decoration-binding`, which copies the binding into the MSL attribute.
That SPIR-V feeds Metal only; the SPIR-V that ships is the first run's, with
the Vulkan sets.

The registers are the source of truth because SDL's Direct3D 12 and Vulkan
conventions already require them to be in this order: textures before storage
buffers in `t`, read-write textures before read-write buffers in `u`. A shader
whose registers break that order is refused by the build with the register
named, rather than compiled into something Metal binds wrong.

The shipped shaders' MSL is byte for byte what it was, which the recompile
confirmed; only the compute test shader changed.

## Alternatives considered

- **Write the Metal order into the registers.** The register numbers are shared
  with Direct3D 12 and Vulkan, whose order differs. One source cannot carry
  two numberings.
- **Rewrite the `[[buffer(n)]]` attributes in the emitted MSL.** The text names
  resources by names SPIRV-Cross chose, and a read-only and a read-write
  storage buffer come out with the same `device` qualifier, so the rewrite
  would have to recover from the text what the registers say plainly.
- **SDL_shadercross.** It does exactly this through SPIRV-Cross's API and is
  the tool SDL recommends. It is a further native build for a step that two
  tools and twenty lines of Java already cover.

## Consequences

- A shader that combines uniforms with storage resources in one stage binds
  correctly on Metal. `MetalBindingsTest` holds the order.
- A new shader is compiled the same way as before, with one more DXC run that
  the task discards.
- A shader with a resource type `MetalBindings` does not know fails the build
  and names the declaration, so an unknown type is added to the table rather
  than guessed.
