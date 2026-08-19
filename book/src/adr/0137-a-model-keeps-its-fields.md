# 137. A model keeps its fields

Date: 2026-08-19

## Status

Accepted. Repairs the cost
[ADR-0134](0134-a-write-is-rewritten-wherever-it-is.md) accepted and
[ADR-0136](0136-an-application-is-values-actions-views.md) wrote down as
unavoidable.

## Context

ADR-0134 let a separate class assign to a model's `@Bind` fields, and charged for
it:

> **A model's fields go from `private` to package-private** the moment its actions
> move out. That is a real loss […] and it is the direct price of the split.

It was presented as arithmetic — javac cannot see a private field from another
top-level class, therefore the fields open up — and the arithmetic is right for
the case it considered. It considered the wrong case. Two top-level classes in one
package is not the only way to have two classes.

**Nestmates** (JEP 181, Java 11) share private access in both directions. A nested
`Actions` reads `values.clicks` with an ordinary `getfield` and calls a private
method with an ordinary `invokevirtual`; javac emits no accessor and the verifier
is satisfied. So the whole cost was avoidable, and was paid for a commit.

## Decision

**Nest the actions, and nothing opens up.**

```java
@Model
public final class Settings {

    @Bind("app.gain") private Number gain = 40;

    @Model
    public record Actions(Settings values) {
        @Action("app.louder") public void louder() { values.gain = … }
    }
}
```

Fields stay `private`. The weaver's synthesised setters stay `private` too, and
that is the second half of this record: **the setter's visibility is now derived
rather than fixed.**

The build already walks every class to find writes. It now also asks, for each
model, whether any writer is outside the model's nest — comparing `NestHost`
attributes, which is exactly the question the JVM will ask later. Setters are
emitted `private` unless the answer is yes.

That matters because a package-private `goldberry$set$gain` is a real hole: any
class in the package could call it and set a private field. Small, obscure, and
synthetic — and still a hole that existed only because the weaver could not be
bothered to work out whether it was needed. Now it is only there when it is.

### Sibling top-level classes still work

They are refused nothing. A `Settings` and a `SettingsActions` side by side get
package-private fields (javac's requirement, not the weaver's) and package-private
setters (now the weaver's, following javac's). What changed is that this is the
*fallback* rather than the only shape.

### Nesting is scoping, not coupling

The obvious objection is that the values class now "knows about" its actions. It
does not: `Settings` holds no reference to `Actions`, names it in no signature,
and compiles with it deleted. A nested type is a name inside a namespace. The
arrow still points one way.

## Consequences

`ShowcaseModel` is one file again — 272 lines, of which the first 120 are values
and the rest a nested record. That is the trade this makes: **one file, or open
fields.** ADR-0136's guide recommends the nested form and says why; an application
that would rather have two files can still have them.

**A model written to from two nests gets package-private setters for all its
fields**, not just the ones written from outside. The analysis is per model, not
per field, because a finer one would buy nothing — a model's actions are one class
in practice, and the whole question is whether that class is inside or outside.

**The nest check needs the whole compilation**, like every other cross-class rule
here. A writer in another module is not seen, and is already refused by the
package check.
