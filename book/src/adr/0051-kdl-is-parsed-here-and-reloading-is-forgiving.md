# ADR-0051: KDL is parsed here, and reloading is forgiving

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §1, §8, §9; [ADR-0004](0004-three-tree-retained-declarative-model.md), [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md), [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)

## Context

§9 makes KDL 2.0 the markup language and calls the schema "the stable contract".
§1 and §8 say markup and stylesheets are hot-reloadable at runtime. Neither says
where the parser comes from, and neither says what "reloadable" does when the
file being reloaded is halfway through being typed.

## Decision

**The KDL 2.0 parser is written here.** KDL 2.0 landed recently and has no mature
Java implementation; the language is small, and the parser has to produce the
source positions §9 requires — which general-purpose parsers usually discard,
because most consumers do not need to say *where* a node was. Same reasoning as
[ADR-0010](0010-hand-written-ffm-bindings.md) applied to a document format.

Three things about KDL 2.0 that a parser written from memory of 1.0 gets wrong,
and that have tests naming them:

- **Keywords are `#`-prefixed.** `#true`, `#false`, `#null`, `#inf`, `#nan`. Bare
  `true` is not a boolean and is not a legal argument at all.
- **Raw strings are `#"…"#`,** fenced by the number of `#`, not `r"…"`.
- **Block comments nest.** `/* a /* b */ c */` is one comment. A scanner that
  stops at the first `*/` then chokes on the remainder.

**The subset refuses two things by name.** Type annotations (`(u8)123`) and
multi-line strings (`"""…"""`). Both are real KDL and neither has a use in a
widget schema; refusing them with a message that says so beats accepting and
discarding, which is how a document that says something the toolkit ignores looks
like it worked. Same stance as the CSS engine
([ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)).

**The inflater is generic in what it builds.** A `Factory<T>` takes a node and its
already-inflated children. The widget tree does not exist
([ADR-0004](0004-three-tree-retained-declarative-model.md)) and the inflater does
not need it to: the showcase can inflate to a `Box`, a widget tree will inflate to
widgets, and neither requires this class to change. Depth-first, so a factory is
never handed children it has to inflate itself.

**Registering a name twice is refused; `replace()` is how you shadow.** §9 says
built-ins and application widgets register identically, which means an
application *can* override a built-in. Silently, at whichever point its
registration happened to run, is not a good way to discover that it did.

**Reloading is forgiving, and loading is not.** This is the decision worth the
record.

`ReloadableSource.load` is **strict**: a broken stylesheet at start-up is a bug,
there is no last good value to fall back to, and rendering unthemed while saying
nothing is the worst available outcome.

`reload()` is **not**. A file being edited is broken more often than it is whole —
every keystroke between `{` and `}` is a parse error — so a failed reload keeps
the last good value, logs once, and waits for the next save. Three details make
that actually work:

- **The failed text is not remembered as "last seen".** Otherwise a file edited
  into an error and then fixed back to its previous contents would compare equal
  and never reload.
- **Identical text produces nothing.** Watchers report one save several times and
  editors autosave; restyling for an unchanged file is work nobody asked for.
- **A file that cannot be read at all is not a failure either.** A rename-into-
  place caught mid-flight looks exactly like a deleted file, and resolves itself
  on the next event.

**The watcher hands its callback to an [Executor], and the default is the UI
thread.** Watching blocks, so it runs on a daemon thread; applying must not,
because everything it touches is UI-thread-confined
([ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md)). A reload that
restyled from a background thread would be a data race that only appears under
somebody's autosave.

## Alternatives considered

- **Depend on a KDL library.** Rejected: none is mature for 2.0 in Java, and §9
  makes the markup schema a stable contract — owning the parser is what lets the
  toolkit hold that promise rather than inherit somebody else's interpretation.
- **Reload by re-reading only the file the watch event named.** Rejected: an
  editor that renames a temporary file into place reports a change to a name that
  is not the one being watched. Re-reading every source costs a string compare.
- **Fail loudly on a broken reload, like loading does.** Rejected, and the reason
  is the whole point of the feature: the file is broken because somebody is
  typing in it.
- **Debounce by waiting a fixed delay after the first event.** Rejected in favour
  of a quiet period that restarts on every event, because a large file saved in
  several writes takes longer than any fixed delay worth choosing.

## Consequences

- **Hot reload works for stylesheets today and for markup the moment there are
  widgets.** `ReloadableSource` is parameterised on the parser, so a `Stylesheet`
  and a `List<KdlNode>` reload through the same type; both are tested.
- **The §9 example document is a test.** The settings window in
  `ARCHITECTURE.md` is parsed and asserted on, so the documented markup cannot
  drift from what the parser accepts.
- **On macOS a change can take seconds to be noticed.** The JDK's `WatchService`
  has no kernel backend there and polls. Nothing here can fix it; it is in the
  class documentation because "hot reload is broken on my Mac" is otherwise a
  puzzle.
- **`bind` and `action` are not implemented.** §9 wants `Kdl.inflate(doc).bind(controller)`
  with explicit wiring and no reflective handler lookup. The lookup half — finding
  a node by `id`, and refusing a duplicated one — is here; binding needs a widget
  with an action to bind, and arrives with the controls.
- **Nothing enforces the parity invariant yet.** §9 requires every built-in widget
  to be constructible as Java, as KDL and as CSS-styleable, "enforced by test".
  There are no built-in widgets, so there is nothing to enforce it over; the test
  belongs with the first control.
