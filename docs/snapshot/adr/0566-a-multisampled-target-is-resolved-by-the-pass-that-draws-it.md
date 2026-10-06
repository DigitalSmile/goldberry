# ADR-0566: A multisampled target is resolved by the pass that draws it

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-007),
  [ADR-0563](0563-a-texture-has-layers-levels-and-samples-and-a-pass-may-have-no-colour-target.md)

## Context

ADR-0563 gave `TextureSpec` a sample count and the device the rule that a
multisampled texture is a render target alone, with one layer and one level,
and left the one thing a multisampled target exists for unwired: nothing
could draw into it with a pipeline of its sample count, and nothing could
bring its samples down into a texture a shader reads. A renderer's board has
tilted card edges that stair-step at one sample, and a post-process AA pass
was its alternative; SDL has the primitive, a resolve store op on the colour
target, and a pipeline's `multisample_state`.

## Decision

**A pipeline says its sample count**, `PipelineSpec.builder(…).samples(4)`,
with one the default, and a pass refuses a pipeline whose count is not its
targets'.

**The pass that draws a multisampled target names where its samples go.**
`frame.renderPass(msaa, load, resolved.level(0), body)`, with a `DepthTarget`
overload, takes a `TextureView` of a single-sampled texture of the target's
format and size, and ends the pass with SDL's `RESOLVE` store op into it.
The multisampled texture's contents are then undefined, which is the store
op's meaning and what a target that is never sampled can afford; a renderer
that needs the samples kept has no such renderer yet, and `RESOLVE_AND_STORE`
waits for one. A depth target in a resolving pass must have the colour
target's sample count, checked where the pass begins.

**The resolve is a view, not a texture**, so a level of a mipmapped texture
or a layer of an array can receive it, as every other sub-resource the API
addresses can.

## Alternatives considered

- **A separate resolve command**, after the pass. Vulkan has one; SDL's
  design is the store op, and following it keeps the pass as the one place
  attachments are described.
- **Multisampling on `Canvas3d`**, a `samples` attribute whose layer draws
  into a multisampled texture and resolves into the layer's. Nothing asked
  for it; a renderer that wants it makes its own target, as the showcase's
  cube would. It can join as an attribute later without changing this.
- **A post-process AA pass shipped by the toolkit.** The renderer's
  alternative, and still open to it; the toolkit draws no scene of its own
  to need one.

## Consequences

- `PipelineSpec` has ten components; its nine-argument constructor stays.
  `SdlGpuPipelineDescription` likewise, with `SdlGpuSampleCount`.
- A multisampled target's contents after a resolving pass are undefined, by
  design; a `Load.keep` on the next pass into it reads garbage.
- `GpuApiTest` checks the resolve on lavapipe: a tilted edge resolves to
  partial coverage where one sample gives all or nothing, and every
  mismatch is refused in Java.
- With this, every item of the Gwent clone's issue list is closed.
