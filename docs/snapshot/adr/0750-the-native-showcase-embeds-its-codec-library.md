# ADR-0750: The native showcase embeds its codec library

- **Status:** Accepted
- **Date:** 2026-11-28
- **Relates to:** ADR-0159, ADR-0651, ADR-0746

## Context

Showcase CI built the Media screen into the executable. It supplied the core
and web-view libraries and ThorVG to GraalVM, while the Media screen depended
on a codec library that stayed outside the build. The startup frame test could
pass with the bundled decoders unavailable.

## Decision

Showcase calls the reusable Media workflow and downloads the target's
`media-natives` artifact from the same run, as it does for Vector. The image
build takes the host classifier from `:media` and puts it on the image classpath.
The Media loader reads the codec library and its layout from the classifier
resources and validates the ABI. An explicit media or vector directory
requests packaging and the classifier task fails if its descriptor is absent.

Before artifact upload, CI runs `--verify-native-libraries` against the image.
The command checks the media and web-view loaders and parses a small SVG.
The existing frame run checks startup and rendering through the core library.

## Consequences

The three Showcase artifacts carry the four Goldberry native libraries in one
executable per target. Web views and system decoders still use the host's
runtimes. Contributors can build an image with the optional libraries already
built on their machine or supply the two artifact directories explicitly.
