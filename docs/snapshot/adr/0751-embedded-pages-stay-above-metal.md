# ADR-0751: Embedded pages stay above Metal

- **Status:** Accepted
- **Date:** 2026-11-28
- **Relates to:** ADR-0458, ADR-0479, ADR-0491, ADR-0666, ADR-0674

## Context

The Cocoa backend could claim Metal and then give up GPU presentation as soon
as the Showcase opened an embedded page. The WKWebView shim placed the page
above existing subviews. SDL could later add a Metal view above it on a claim
or reclaim. The presentation policy used the CPU to avoid that ordering.

The Showcase room offered the software and hardware tracing tiers. Hardware
checked the independent Vulkan device. The software tier has compute shaders
for the canvas device, including Metal.

## Decision

The macOS superbuild patches SDL to insert each Metal view below the content
view's other subviews. AppKit's positioned insertion keeps a page above the
Metal view even when the page was opened first. Cocoa windows therefore keep
GPU presentation with an embedded page.
[AppKit subview ordering](https://developer.apple.com/documentation/appkit/nsview/addsubview(_:positioned:relativeto:))

The room offers **Auto** to prefer a hardware ray-tracing GPU when available
and otherwise use the canvas's compute shaders. **GPU compute** explicitly
selects the canvas path, and **Hardware** explicitly requests the Vulkan
hardware tier.

## Verification

The page-stacking policy has a unit test. The macOS first-thread GPU lane
checks the order of a WKWebView and SDL's Metal view after a claim and reclaim.
The patch applied twice to the pinned SDL source on Linux. The room tests
passed on lavapipe, including a run where the hardware probe found no matching
Vulkan device and Auto painted the room with GPU compute. The native view
stacking still needs a macOS run.
