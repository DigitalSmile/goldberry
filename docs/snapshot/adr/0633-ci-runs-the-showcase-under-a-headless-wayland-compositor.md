# ADR-0633: CI runs the showcase under a headless Wayland compositor

- **Status:** Accepted, not yet run
- **Date:** 2026-10-18

## Context

The toolkit prefers SDL's Wayland driver, and on Linux it defaults to X11 for
now (ADR-0086). Nothing in CI ran the Wayland driver. The verify job runs every
suite headless, `showcase.yml` runs the native image under `xvfb-run`, which
is X11, and the library's `WAYLAND` capability bit says only that the driver was
compiled. Two decoration bugs shipped without a red tick because X11's window
manager decorates the window and libdecor is never reached (ADR-0084). Popups
on Wayland are `xdg_popup`s, placed by the compositor, and since ADR-0632 they
are larger than their panels by a shadow's room; nothing had ever opened one in
CI.

No machine here can run it either. No headless compositor is installed, and the
showcase run against the live GNOME session is what once took the shell down
(Mutter's `wl_client_destroy`, recorded in `docs/status.md`).

## Decision

**`linux.yml`'s verify job runs the showcase for 120 frames under weston's
headless backend, on `linux-x64`.**

- weston is installed from the runner's packages and started with
  `--backend=headless --renderer=pixman` on a socket and a runtime directory of
  its own: a real compositor that speaks xdg-shell, places popups and sends
  frame callbacks, with no output.
- The showcase runs on the JVM through `:example:run` against the library the
  job downloaded, with `goldberry.backend.videoDriver=wayland`, `DISPLAY`
  unset so SDL cannot fall back to X11, and `goldberry.example.frames=120`.
- The gate is the launcher's own `painted 120 frame(s); exiting`, the line the
  native image's step greps for. A crash, a hang, or a library whose SDL has
  no Wayland driver fails it, and weston's log is printed on failure.

The JVM and not the native image, because the native image is built only on a
tag or by hand (ADR-0340), and this leg is about the driver, which is the same
code either way. One leg, because what it tests does not differ by CPU.

Rejected:

- **`sway --headless`**: a tiling compositor would place and size the window
  itself, and the leg would test sway's layout as much as the toolkit's.
- **Asserting a frame budget**: weston's headless output has a refresh rate,
  but nobody has measured what a runner holds, and ADR-0452's reason for asserting
  none under Xvfb applies here unchanged.
- **Running the suites under Wayland**: they are headless by design. The leg is
  about a window, popups and frame callbacks, which only the showcase has.

## Consequences

- Only a CI run proves the step works. It was not run before it was
  committed, and the first run may fail in the step itself: the weston flags
  are those of the 24.04 runner's weston 13, and a missing package or a
  changed flag is the likeliest first red.
- A Wayland regression in the driver, in libdecor's path or in popup
  placement now fails a pull request instead of reaching a user.
- The CI-matrix table in the guide gains a clause at release
  (`docs/snapshot/contributing-testing-ci-matrix.md`, item 4).
