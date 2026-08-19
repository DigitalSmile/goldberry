# 134. A write is rewritten wherever it is

Date: 2026-08-19

## Status

Accepted. Removes the known gap
[ADR-0125](0125-a-raw-field-is-woven-into-a-binding.md) shipped with, and is what
makes [ADR-0136](0136-an-application-is-values-actions-views.md) possible.

## Context

ADR-0125 rewrote `putfield` **inside the declaring class only**, and listed the
consequence honestly:

> **A field written from outside its declaring class is not observed.** An inner
> class assigning to its outer's `@Bind` field compiles to a `putfield` in a
> *different* class, which this transform never sees. […] the failure is silent.

That was tolerable while a model was one class holding both its values and the
methods that change them. It stops being tolerable the moment anybody wants the
two apart — which is the ordinary request, and the one that arrived. A
`ShowcaseActions` assigning `model.clicks++` compiled, ran, changed the field,
and notified nobody.

Every way around it was worse. Public setters on the model are the `get`/`set`
this whole redesign deleted. Making the actions a *nested* class works, because
nestmates share private access — but then "separate class" means "same file", and
the model is not distilled at all.

## Decision

**Rewrite a write to a woven `@Bind` field wherever it appears in the same
compilation.**

The weaver already made two passes — one to learn which classes are models, one
to weave them — so pass one now also records each model's rewired fields, and
pass two rewrites `putfield` against any of them, in any class. A class that is
not a model and writes to no model is still not touched at all.

Two things follow:

- **The synthesised setter is package-private**, not private. A sibling class has
  to be able to call it.
- **A write from another package is refused**, at build time, naming both
  classes. The setter is package-private, so the call would not verify; an
  `IllegalAccessError` at the first click is not an acceptable way to find that
  out.

Constructors keep their exemption, but only for the class's *own* fields: nothing
can have subscribed to a model still being constructed, while a write to somebody
else's model from inside a constructor is an ordinary write to an object that was
built long ago.

## Consequences

**A model's fields go from `private` to package-private** the moment its actions
move out. That is a real loss — ADR-0125 was pleased that a model kept its fields
private — and it is the direct price of the split. It is not `public`: nothing
outside the application's own package can reach a field, and the alternative was
accessors, which are worse and reach further.

An application that keeps its values and methods in one class loses nothing and
should carry on doing so. The split is available, not required.

**The rewrite is per compilation, not per program.** Pass one sees one tree of
classes — one module — so a class in module B writing to a model in module A is
not rewritten. In practice that is the same rule as the package check, since JPMS
forbids split packages, and it is worth stating rather than discovering.

**The bug that found the implementation was mine.** Composing two
`transformingMethodBodies` with complementary predicates — one for constructors,
one for everything else — silently drops every rewrite: the second pass no longer
sees the code elements the first handed on. Every notification test failed at
once, which was the good outcome; the fix is a single transform that picks per
method.
