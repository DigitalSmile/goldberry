# ADR-0029: Yoga's node API, and who owns a node

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §3.1, §5, §8, [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0017](0017-proving-the-struct-by-value-upcall.md), [ADR-0019](0019-the-backend-spis-first-cut.md), [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md)

## Context

[ADR-0017](0017-proving-the-struct-by-value-upcall.md) proved the hardest part of
the Yoga binding in isolation: a Java upcall returning `YGSize` **by value** is
called from C and arrives intact. What it could not prove is that Yoga calls it.
The proof went through `goldberry_probe_measure`, a C function written for the
purpose, because at that point there was no node to attach the callback to. The
last line of that record says so: *what remains is binding Yoga's node API so the
callback is driven by a real layout pass.* This is that.

Binding it turns out to be less about function signatures than about ownership.
Three things make Yoga's C API awkward to expose directly:

**It has no ownership model.** `YGNodeFree` takes a pointer and frees it. Nothing
in the API says which pointers a tree holds, and freeing a parent says nothing
about its children — `YGNodeFreeRecursive` exists precisely because callers get
this wrong. On the Java side the hazard is sharper than in C: a `YogaNode` object
holding a freed pointer is a perfectly live Java object that segfaults the process
on its next method call, with no stack trace pointing at the mistake.

**Its preconditions are `abort()`.** A node may not have both children and a
measure function. A node may not be inserted into two trees. A node may not
become its own ancestor. Yoga enforces all of these with assertions that call
`abort()`, which takes the JVM with them: no exception, no stack, nothing to
catch, and — in the cycle case — a stack overflow inside native code rather than
a message.

**It disagrees with CSS, quietly.** Yoga defaults `flex-direction` to `column`
where CSS says `row`, and `flex-shrink` to `0` where CSS says `1`. It also snaps
computed positions to whole *logical* pixels by default, which is exactly wrong
on a fractional display: half the edges in a 1.5× window land mid-physical-pixel
and the compositor smears them.

And one shape problem: Yoga splits every length-valued property across two or
three functions — `YGNodeStyleSetWidth`, `YGNodeStyleSetWidthPercent`,
`YGNodeStyleSetWidthAuto` — and expresses "unset" by passing `YGUndefined`, a
NaN, to the first of them.

## Decision

`YogaNode` is the layout engine as the rest of Goldberry sees it. Seven decisions
make it up.

**The tree owns its nodes, and the ownership is one-directional.** A node from
`YogaNode.create()` is a root and the caller owns it. `insertChild` transfers
ownership to the parent, and from then on `close()` on the child is refused —
freeing it there would leave Yoga's own child list pointing at released memory.
`close()` on a root frees the whole subtree **child-first**, marking each Java
wrapper dead as its pointer goes, so a reference kept to a descendant throws
`IllegalStateException` on use instead of reading freed memory. `removeChild`
hands ownership back: the removed node is a root again, and the caller's problem.

**Every reachable Yoga abort is a Java exception first.** Children on a measured
node, a second parent, a cycle, an out-of-range index, `markDirty` on a node with
no measure function, a config closed under its nodes. Each is checked in Java and
reported with a message that says what was violated and why Yoga cares.

**A tree belongs to one thread, and every method checks.** Yoga has no locking of
any kind. A tree touched from two threads does not fail — it corrupts, which is
strictly worse than failing. The UI thread is where layout belongs anyway
([ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md)), so the check
costs nothing that matters and turns the worst failure mode into the most obvious
one.

**`StyleLength` puts the split setters back together.** A sealed interface —
`Points`, `Percent`, and a `Keyword` enum of `AUTO` and `UNDEFINED` — dispatched
by an exhaustive `switch` with no default arm, so a kind of length added later
fails to compile everywhere it is not handled. `UNDEFINED` is what reaches Yoga
as a NaN, so NaN never appears in the API: it is one state with one spelling,
rather than a value that does not equal itself. Where Yoga exports no `*Auto`
function — `min-width`, `max-width`, `inset` — `AUTO` is refused by name rather
than dropped, because a stylesheet silently having no effect is the harder bug.

**The binding class is package-private.** `Sdl` and `SdlVideo` are public because
`:core` drives SDL directly. Nothing above `:natives` drives Yoga directly, so
`Yoga` — the class holding the sixty-odd `MethodHandle`s — is package-private and
`YogaNode` is the only way in. That means there is no second path to
`YGNodeFree`: no way to free a node without going through the tree that knows
which wrappers are still alive.

**`YogaConfig` makes the two CSS deviations explicit.** It turns web defaults on
by default, and it exposes the point scale factor without guessing one — only the
backend knows what display a window is on, and a wrong guess is worse than the
default because it looks right until the window moves. This is the mechanism
behind the fractional-DPI claim in
[ADR-0019](0019-the-backend-spis-first-cut.md): set it to the window's display
scale and Yoga rounds to physical pixels, so a 1px border is one crisp device
pixel at any scale. A config refuses to close while nodes it made are alive.

**Measure failures are collected across the tree, not thrown at the first one.**
A layout pass can fail in several callbacks, and by the time Yoga returns they are
all holding an exception. Throwing at the first one found would leave the others
pending, and the *next* pass would then fail with an exception from the pass
before it. So the tree is walked, every pending failure is taken, the first is
thrown and the rest are attached as suppressed.

All 48 of Yoga's enumerators the bindings model are registered in the layout
table and checked against the compiled library, under the rule
[ADR-0010](0010-hand-written-ffm-bindings.md) sets. This is where that rule earns
its keep: `YGAlignCenter` is 2 and `YGJustifyCenter` is 1, and getting that pair
backwards produces a layout that is merely off-centre — never an error, on every
platform at once.

## Alternatives considered

**Bind `YGNodeFreeRecursive` and let Yoga free the tree.** One call instead of a
walk, and it is the function Yoga provides for exactly this. It also frees
pointers Java wrappers are still holding without those wrappers ever learning
about it, which is the entire hazard. Freeing one node at a time is what makes it
possible to mark each wrapper dead as its pointer goes.

**A pointer-to-wrapper map instead of a Java-side child list.** It would avoid
keeping two views of the same tree. It costs an identity-map lookup per child
access on the layout path, and it does not solve the freeing problem — the map
would still have to be walked to mark wrappers dead. The duplicate list is
cheaper and the two views are asserted to agree, against Yoga's own
`YGNodeGetChildCount`.

**`Cleaner` or an `Arena` for automatic freeing.** Attractive, and wrong here:
GC order is arbitrary, and a node freed before its parent leaves the parent
holding a dangling child. Layout trees are also large and short-lived, so
non-deterministic freeing would mean an unbounded amount of native memory waiting
on a collection that has no reason to happen.

**Expose the split setters as Yoga declares them.** `setWidth(float)`,
`setWidthPercent(float)`, `setWidthAuto()`. Honest to the C API, and it puts the
choice of function at every call site in the CSS compiler while making "unset
this" read as "set this to not-a-number".

**A `float` with `Float.NaN` for undefined, no `StyleLength` at all.** Fewer
types and no allocation. It also gives one state a spelling that does not compare
equal to itself, and it cannot express `percent` at all without a second
parameter — at which point it is `StyleLength` with worse ergonomics.

**Skip the thread check.** It is a comparison per call on the layout path.
Keeping it is a judgement, not a measurement: the cost has not been benchmarked,
and if a profile ever shows it the check can move behind an assertion. Silent
corruption is worth more than a comparison until then.

**Register only the enum values a widget is likely to use.** The registry gains
48 rows for constants most stylesheets will never mention. The ones nothing
checks are precisely the ones that will be wrong, and discovering that through a
subtly misaligned layout is the failure this whole mechanism exists to prevent.

## Consequences

**The last functional gap in M0 is closed.** The measure callback is driven by
Yoga itself, from a real layout pass, with the constraints the flexbox algorithm
arrived at — `YogaMeasureTest` asserts that a leaf inside 200 points of width and
10 of padding is asked to measure at 180. That is proven on linux-x64; the same
tests run on every target in CI, so the other five are answered by the next run
rather than by argument.

**The ABI version is now 5, and the Java and native artifacts must be rebuilt
together.** The shim gained 48 constant rows and the export list gained 69
symbols, so a `libgoldberry` built before this change is refused at load time with
a version mismatch rather than a segfault. This is the mechanism working as
intended, but it does mean a stale local library has to be rebuilt:
`./gradlew :natives:cmakeBuild`.

**The export list is now mostly Yoga.** 69 of its 99 symbols. The MSVC `.def`
and Mach-O `-exported_symbols_list` branches of the export machinery have never
run at all, so the first Windows or macOS build is now testing them against a much
larger list than the one that motivated
[ADR-0018](0018-sdl-conventions-stop-at-the-boundary.md).

**Two views of the tree exist and can only disagree through a bug.** `YogaNode`
keeps its own child list so that `children()` can return wrappers rather than
pointers. A test asserts it against `YGNodeGetChildCount` after every structural
operation, which is the only thing standing between the duplication and a class
of bug that would otherwise be invisible.

**Nothing in `:core` uses any of this yet.** It is a binding, not a widget tree:
the three-tree model in [ADR-0004](0004-three-tree-retained-declarative-model.md)
still has no stateful-widget lifecycle, and nothing decides when a layout pass
runs. What is settled now is the layer beneath that decision.

**Deliberately not bound.** Baseline functions — so `Align.BASELINE` behaves as
`FLEX_START` until the text stack can supply one; `YGNodeClone` and
`YGNodeCopyStyle`; node and config contexts; the dirtied callback; errata and
experimental features; and the style **getters**, since the CSS layer is the
authority on what a node's style is and reading it back from Yoga would create a
second one. Yoga 3.1 also has no `YGNodeStyleSetPositionAuto`, so CSS's
`inset: auto` has nothing to compile to.
