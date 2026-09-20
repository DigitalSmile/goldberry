# 440. The accessibility bridge is on hold, and the semantics tree stays

Date: 2026-09-20

## Status

Accepted. Puts on hold what `docs/ARCHITECTURE.md` §13 has planned since before
this log existed, and what `book/src/status.md` has listed under M5 since M5 was
written. Supersedes the schedule in
[ADR-0077](0077-disabled-propagates-for-input-and-not-for-paint.md) §Consequences
and in [ADR-0225](0225-a-toast-says-it-is-worth-interrupting-for.md), neither of
whose decisions changes.

**On hold rather than deferred, and the distinction is the whole record.** A
deferral names a later milestone; this names none, and nothing in the repository
should be written as though one existed.

## Context

`docs/ARCHITECTURE.md` §13 says screen-reader bridging "is planned via
**AccessKit** (C ABI, fits the FFM stack) in a post-v1 milestone — but the
semantics tree exists from the start precisely so this is an adapter, not a
rearchitecture." Nine entries in `book/src/TODO.md` wait on it, and the list is
emphatic that they should: *"a role nothing consumes is a value written for a
bridge that does not exist"*, *"adding `LINK` and a landmark now would make this
gap look closed"*.

The interface was never the difficulty. `accesskit_c` is a C API over the core
data structures and all three platform adapters — one API where the alternative
is three — and it can be had either by driving cargo from CMake through
Corrosion or by consuming the project's prebuilt package, which exists so that
toolkit developers need not deal with Rust at all.

## Decision

**The bridge is not being built, and no milestone owns it.**

### Why, in the toolkit's own words

The nine entries each refuse to write a constant until something consumes it.
That argument applies one level up, and it is the reason this is a decision
rather than a delay:

- **Nothing has asked.** `TODO.md` §12 already has a bucket for work waiting on
  a consumer rather than on a decision — a clipboard watcher, a primary
  selection, a cached canvas layer. This joins it. `Role`'s own javadoc says a
  role nothing implements "is a promise to an assistive technology that nothing
  keeps"; a bridge nobody has asked for is the same promise, one level larger.
- **Two of its three platforms cannot be run here.** UIA and NSAccessibility are
  behind the same missing Windows and macOS machines that `TODO.md` §12 already
  blocks the tray, the MSVC `.def`, the Mach-O visibility branch and the
  transparent-popup corners on. An accessibility bridge is not a feature that can
  ship untested on two thirds of its surface: the failure mode is silent, and the
  people it fails are the ones with no other way in. Shipping the AT-SPI third
  alone would put "screen reader support" in a README that is false on two
  desktops.
- **The cost is permanent and it is in `:natives`.** Either a Rust toolchain in
  the superbuild on four platforms and in CI, or four platforms of vendored
  prebuilt binaries in the release — which is the same licence-and-provenance
  question `TODO.md` has open and unanswered for PDFium. Neither is wrong; both
  are a standing obligation taken on for something with no consumer.

### What stays, and why it is not dead weight

`Role`, `Live`, `Semantics` and `SemanticsSweepTest` all stay exactly as they
are. They are not a rehearsal of a bridge that is not coming:

- **The sweep pays for itself today.** `docs/testing.md` §1.7 walks the gallery
  and asserts that every interactive node exposes a role and a name. What that
  buys, in `Role`'s own words, is "that the catalog cannot grow a focusable
  widget that has no name, which is the defect an accessibility pass finds late
  and expensively". That is true whether or not anything reads the tree.
- **It is the seam.** §13's claim that a bridge would be "an adapter, not a
  rearchitecture" is the part worth keeping true. The data being in the tree is
  what keeps the door open at no running cost.

So nothing is removed and nothing is added. The nine entries move from **open**
to **on hold**, which in `todo-sweep.md`'s legend is *answered*: decided not to
build, with the reason recorded.

### What §4's baseline still means

`design-system.md` §4's accessibility baseline is **not** withdrawn, and most of
it never depended on a bridge. Keyboard reachability, the focus ring, contrast
authored to WCAG AA, hit targets, reduced motion and text scale to 150% are
built, and every one of them is checked by something. What is now explicitly not
provided is the screen-reader half, on every platform.

That should be said plainly where a reader looks for it rather than implied by
the absence of a milestone, which is why this ADR is cited from `README.md`,
`status.md` and `ARCHITECTURE.md` rather than only from the entries.

## Consequences

**M5 loses its last toolkit item.** What remains under M5 is the release half —
the publishing chain that has never run, and the three-platform frame evidence M1
is waiting on — both of which are blocked on an account and a tag rather than on
code. Text editing depth and IME preedit are already done.

**Nine entries stop being read as neglected.** They keep their prose, gain a
pointer here, and stay in the list, because each still records a trap and the
reasoning that got out of it — which is what `TODO.md` says the top half is for.

**`Role` will not grow `LINK`, a landmark or a list role**, and the four widgets
whose specifications spent a sentence on what they would say still have nowhere
to put it. `docs/core-widgets.md` keeps those sentences: the specification is
what the catalog would say if something were listening, and it is not wrong for
having been written.

**The way back is a consumer, not a milestone.** If somebody asks — an
application that needs it, or a machine to test the other two platforms on —
this is reopened, and the entries are already written. Nothing here makes that
harder than it was; the decision is that it is not scheduled, not that it is
refused for ever.
