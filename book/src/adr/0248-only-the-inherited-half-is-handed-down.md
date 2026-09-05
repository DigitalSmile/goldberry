# 248. Only the inherited half is handed down

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0142](0142-a-style-handed-down-keeps-its-identity.md).

## Context

The entry names the gap and what it needs:

> A style that really changes still re-resolves its whole subtree, and only the
> inherited properties can matter. ADR-0142 stopped a node handing down a new
> instance for an unchanged value; what it did not do is narrow the comparison to
> the properties a child could actually inherit. So scrolling — which moves a
> transform, and a transform inherits nothing — re-resolves every node inside the
> viewport on every frame of the gesture. The fix needs a notion of which
> properties inherit, which the cascade has and `ComputedStyle` does not.

`ComputedStyle` does have it, and has had it all along: `inheritingFrom` is the
list, and it is two lines — `color` and `typography`. A child reads nothing else
from its parent. Its own comment even enumerates what is deliberately *not*
there, `cursor` and `opacity`, and why.

So the notion existed. What was missing was reading it twice.

## The trap, which is the whole of the difficulty

`Element.stableStyle` returns a value used for **two unrelated jobs**:

```java
self = element.stableStyle(self);   // ← the cache key for children
...
var painted = self;                 // ← and what this node paints
```

Loosening the comparison without separating those would hand back an older
instance whose *transform* is last frame's — and then paint with it. A scrolling
viewport would freeze at its first offset while every child cached happily. The
narrowing is a one-line change; doing it safely is not.

## Decision

**Two variables, because there are two jobs.**

- `self` stays exactly what the cascade and `restyle` produced. The node paints
  it, transitions observe it, animations apply to it.
- `handDown = element.stableStyle(self)` is the children's cache key, and is
  `self` or an older instance that **agrees with it about everything a child can
  read**.

**`ComputedStyle.inheritsSameAs`** is that comparison, and it lives beside
`inheritingFrom` deliberately: they are the same list read two ways, and a
property that starts inheriting has to be added to both or the cache goes stale
rather than merely cold.

## Alternatives considered

- **Compare `equals` minus the transform.** It fixes scrolling and nothing else,
  and the next property nobody inherits — `opacity` under a fade, `decoration`
  under a hover — is the same bug again under a different name. The question is
  not "which properties change often" but "which properties a child can see".
- **Have `stableStyle` return both.** A two-field return for a method whose
  callers want different things, where two locals say it plainly.
- **Give `ComputedStyle` an `inherited()` projection** and key children on that.
  It allocates a second record per node per frame to avoid comparing two fields.
- **Let the child compare its own inherited values** instead of keying on
  identity. That is the scheme ADR-0142 replaced: identity is what makes the
  check free, and a value comparison per node per frame is the cost this
  machinery exists to avoid.

## Consequences

- **Scrolling stops re-resolving the viewport.** A transform moving is now
  invisible to every node under it, which is what it always was semantically and
  what the cache now agrees with.
- **Three tests, all in `StyleIdentityTest`.** A parent whose transform moved
  hands down the same instance; a parent whose *colour* moved hands down a new
  one, because `color` inherits and a child that kept its style would be drawn
  wrong; and — the one that matters most — the parent **still paints the
  transform it resolved**.
- **That third test was written against the mistake, and catches it.** Folding
  the two roles back into one variable fails it, which was checked rather than
  assumed. It is the difference between a performance change that is correct and
  one that merely runs faster.
- **`inheritsSameAs` is public**, because it is a claim about the record rather
  than about the renderer, and the comment beside `inheritingFrom` is what keeps
  the two in step.
