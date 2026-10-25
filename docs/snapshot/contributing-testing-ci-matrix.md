# CI matrix changes held back until the release

For `book/src/contributing/testing.md`, `## The CI matrix`.

1. Change the trigger cells of these rows:

| Workflow | Trigger |
|---|---|
| `linux.yml` | Pull request not only touching `site/`, and every push through `snapshot.yml` |
| `macos.yml`, `windows.yml` | Pull request not only touching `site/`, and every push through `snapshot.yml` |
| `snapshot.yml` | Push to `master` not only touching `site/` |
| `codeql.yml` | Pull request not only touching `site/`, and weekly |
| `qodana.yml` | Pull request and push, neither only touching `site/` |

2. Replace the `pages.yml` row's last cell with: "The landing page and this
   book, after `SiteTest`".

3. After the paragraph that begins "The per-OS workflows have no `push`
   trigger of their own", add:

A change to `site/` alone runs `pages.yml` and nothing else. The landing page is
not built into anything the library ships, so the workflows that build the
library ignore it, and `pages.yml` runs `SiteTest` itself. A change to `book/` is
never skipped, because `BookTest` and `BookMarkupTest` check the book against the
code. `SiteOnlyChangeTest` holds every workflow to both rules.

4. In the `linux.yml` row's last cell, after "and `:example`'s tests, against
   the libraries it downloaded", add (ADR-0633): "then the showcase for 120
   frames under weston's headless Wayland compositor".

5. (ADR-0641) After the paragraph that ends "fails rather than skips if the
   library does not load, report the right ABI and bind.", add:

A tray needs a notification area, which a virtual X server does not have, so
the live tray tests skip on Linux. The Windows and macOS runners have a desktop
session: `windows.yml` passes `-Pgoldberry.tray.required=true` to its build and
`macos.yml` to its GPU lane, where the tests run on the first thread Cocoa's
status bar needs. There they open a real tray, click its rows as the shell
would, and change its menu while the icon is up.

   And in the `macos.yml`, `windows.yml` row's last cell, after "with
   `native.required` and `webview.required`", add: ", and `tray.required` on
   Windows".

6. (ray tracing on lavapipe) In `### The GPU tests`, after its paragraph, add:

The ray tracer makes a Vulkan device of its own rather than SDL's, so its tests
are in `:gpu:test`, not the GPU lane, and skip wherever no device has the
ray-tracing extensions. `-Pgoldberry.raytracing.device=llvmpipe` points them at
lavapipe, which has the extensions, and `-Pgoldberry.raytracing.required=true`
makes a skip a failure.

   And in the `linux.yml` row's last cell, after "then the GPU tests on
   lavapipe", add: ", the ray tracer's tests on lavapipe, required where its
   lavapipe has the ray-tracing extensions and runs at 256-bit vectors (the
   x64 leg; the arm64 leg's runs at 128 and is refused)".
