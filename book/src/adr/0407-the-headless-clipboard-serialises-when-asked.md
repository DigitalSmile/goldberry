# 407. The headless clipboard serialises when asked, and can say no

Date: 2026-09-19

## Status

Accepted. Closes two entries under "The clipboard" in `book/src/TODO.md` — "The
headless clipboard is eager" and "A refusal is not modelled anywhere".

Finishes a sentence [ADR-0286](0286-a-clipboard-write-is-an-offer.md) wrote about
its own work: "what it cannot model is laziness or a refusal, and it does not
pretend to".

## Context

The two entries:

> It keeps the bytes rather than serialising on demand, so nothing in a test
> exercises the *laziness* the platform imposes; the upcall path is covered in
> `:natives` against the real SDL instead.

> Every write returns a boolean and the in-memory clipboard always returns true,
> so the branch an application writes for "the compositor declined" is only ever
> taken on a real desktop.

Both describe the same gap from two sides: the test double was honest about
*values* and silent about the *protocol*. Neither is a defect in the double — it
was built deliberately simple — and both are branches in application code that no
run of the test suite has ever entered.

The cost of the first one is the more interesting. ADR-0286 established that
laziness is not SDL's taste but the protocol's: there is no eager call for
arbitrary clipboard data in SDL3, and none in X11 or Wayland either, because the
selection owner is *asked* to serialise. An application written against the
headless double therefore learns the wrong lesson — that `write` is where the
bytes are produced — and the shape it grows is one that pays its serialisation
cost per copy rather than per paste. The double taught that, and the real
clipboard then silently forgave it, because a copy that nobody pastes costs
nothing visible.

The second one is smaller but sharper. Every write on `Clipboard` returns
`boolean` precisely because a compositor can decline, and `Clipboard` says so in
its own javadoc — "a refusal is a real outcome rather than an exception". An
outcome that can only be produced on a real desktop is an outcome whose handler
is dead code in CI, and the handler for a refused copy is the one place an
application is supposed to tell the user that nothing was copied.

## Decision

**The headless clipboard becomes its own class, holds suppliers rather than
bytes, and can be told to decline.**

```java
// io.github.digitalsmile.goldberry.render.backend.headless
public final class HeadlessClipboard implements Clipboard {
    public boolean offer(Map<String, Supplier<byte[]>> byMime);
    public HeadlessClipboard refuseWrites(boolean value);
    public boolean isRefusingWrites();
}

// HeadlessBackend
@Override
public HeadlessClipboard clipboard();
```

### A class, and a narrowed return type

It was an anonymous `Clipboard` in a field initialiser with its state in two
`HeadlessBackend` fields. Two test seams and a laziness invariant do not fit
there, and the argument for pulling it out is one this backend has already made
about `HeadlessFileDialogs`: a test that cannot reach `answerWith(…)` has to cast,
and the cast is then the only thing in the test that knows which backend it is
running on. `clipboard()` is narrowed for exactly that reason, which is legal
because the SPI declares `Clipboard` and a subtype is still one.

### The store is `Map<String, Supplier<byte[]>>`

`offer` is the lazy front door and `read` is the only thing that calls a supplier.
So a test can assert the sentence the platform's contract actually makes:

```java
clipboard.offer(Map.of(SHAPE, () -> { produced.incrementAndGet(); return bytes; }));
assertTrue(clipboard.has(SHAPE));
assertEquals(0, produced.get());   // advertised, and never serialised
```

`has` and `types` are answered from the keys, because they are the cheap
questions a paste button asks when its menu opens and `Clipboard`'s own note
forbids making them expensive.

**Each read calls the supplier again.** SDL's request callback runs once per paste
and so does this. Caching the first answer would be the more obvious code and it
would hide the application that re-encodes a megabyte on every paste — which is
the only bug laziness introduces, so it is the one the double must be able to
show.

### `write(Map)` stays eager, and is stored as a supplier anyway

Its signature already holds the bytes; there is nothing left to defer, and
`Image.toClipboard` encodes at copy time on purpose (ADR-0286). So `write` copies
the caller's array once — the array is the caller's and may be reused — and
stores `copy::clone`. One code path, two front doors, and the copy-per-read
behaviour that the previous implementation had is unchanged.

### `refuseWrites(boolean)` covers every write, and changes nothing

`text(String)`, both `write` overloads, `offer` and `clear` all return `false`
while it is on. A compositor declines a *request*, not a type, so a seam that
refused only the byte half would be modelling something no platform does.

**A refused write leaves the clipboard exactly as it was.** This is the part worth
asserting: an application that read `false` and then found its own earlier copy
gone would be looking at a bug this class had invented. Reads keep working for the
same reason — a clipboard that will not accept a new offer still has the old one
on it.

It is **off by default**. This is a test seam, not a new policy: every test
written before it exists behaves identically, which is the only acceptable price
for adding a switch to a double that dozens of tests already depend on.

### Not on the `Clipboard` interface

Neither seam. The refusal needs nothing there — the `boolean` is already in the
SPI and this only makes it reachable. Laziness is the closer call, because
`SDL_SetClipboardData` really does take a callback and a lazy `write` could be
expressed: `boolean write(Map<String, Supplier<byte[]>>)`.

It is not added, and the reason is the memory. ADR-0286's hardest decision was
that an offer's arena is owned by the write and released by an upcall that arrives
*while the next offer is being installed*; a `Supplier` in the SPI would put a
Java lambda on the far side of that boundary, so the offer would have to keep the
supplier, its captured graph and an arena alive together, and a supplier that
throws would be an exception crossing back into C. That is a real cost for a
capability an application can already have by encoding at copy time — which is
what ADR-0286 decided images should do anyway.

## Consequences

- **`HeadlessClipboardTest` holds both halves.** `Laziness` has nine cases, of
  which `nothingIsSerialisedUntilItIsRead`, `theCheapQuestionsStayCheap` and
  `everyReadProduces` are the contract; `Refusal` has five, of which
  `changesNothing` and `offByDefault` are the ones that would catch this class
  growing a policy. `Seams` checks that the two new methods are UI-thread
  confined like every other SPI call and that the narrowed return type makes the
  cast unnecessary.
- **`HeadlessBackend` lost about sixty lines and two fields**, and `clipboard()`
  gained the paragraph explaining the narrowing. The class was already long.
- **`ClipboardDataTest`'s own javadoc was wrong from this commit** — it said the
  double "cannot model the platform's laziness or a refusal" — and now points at
  the class that does. ADR-0286's consequence list says the same thing and is left
  as written: it was true when it was written, and the ADR that changed it is
  this one.
- **The `false` branch of a copy is reachable in CI.** Whether any application
  code actually has one is a separate question this does not answer; it makes the
  question askable.
- **The laziness is still only modelled, not shared.** `SdlClipboard`'s upcall is
  the real thing and `:natives` still tests it against real SDL. What is new is
  that the two now agree about *when* bytes are produced, so a widget tested
  headlessly is tested against the shape it will meet.

## Alternatives considered

- **Keep the bytes and add a `boolean serialisedLazily` flag.** Records the claim
  without making it true; nothing would call a supplier because there would be
  none.
- **Cache the first `read` and count it once.** Simpler, and it makes the
  expensive-per-paste bug invisible, which is the one thing laziness is worth
  testing for.
- **`refuseNextWrite()`, a one-shot, like `HeadlessFileDialogs.answerWith`.** The
  dialog queue models a *user* answering each dialog differently. A compositor
  that declines is a state of the session, not a queue, and a test that wanted
  one refusal can turn the switch off again.
- **Throw from a refused write.** `Clipboard`'s javadoc rejected this before the
  interface existed: "a copy that did not happen must not take the window down
  with it".
- **A separate `LazyClipboard` test double next to the headless one.** Two
  clipboards, one of which every existing test uses and the other of which is
  where the contract lives. The double a test gets by default is the one that has
  to be right.
