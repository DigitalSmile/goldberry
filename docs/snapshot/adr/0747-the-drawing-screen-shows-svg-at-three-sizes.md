# ADR-0747: The Drawing screen shows SVG at three sizes

- **Status:** Accepted
- **Date:** 2026-11-25
- **Relates to:** ADR-0743, ADR-0744, ADR-0746

## Context

The Drawing screen already shows raster image fits and icon sizes. An SVG
example also needs to show what happens to a document’s own colours when
the application changes theme.

## Decision

Show two original inline documents at 24, 48 and 72 logical pixels. The
hills use fixed fills. The leaf uses `currentColor` and inherits the theme
accent. Only the first copy in each row is a named figure. A third, invalid
document demonstrates the error icon and alt text. When the vector library
is unavailable, the card identifies the native dependency.

The showcase declares a module dependency on `dev.goldberry.vector` and
loads `VectorStyles.stylesheet()`. The card links to the published image
section until the new heading is released.

## Consequences

The documents travel inside the showcase classes, including in its native
image. The parked Drawing section supplies the catalogue row and runnable
KDL examples. Including `vector` in the guide’s module list lets the existing
checks detect a missing heading or catalogue row.

The example tests normally exercise the missing-library message. These commands check
the Drawing card against a local Linux x64 build.

```sh
./gradlew :vector:nativesBuild
./gradlew :example:test --tests '*DrawingChapterTest' \
    -Pgoldberry.vector.libdir="$PWD/vector/build/vector-natives/linux-x64/install/lib" \
    -Pgoldberry.vector.required=true
```

The pixel test checks the size and ink of both drawings in the light and dark
themes and the error state of the invalid document.
