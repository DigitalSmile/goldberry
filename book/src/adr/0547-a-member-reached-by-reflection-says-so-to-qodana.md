# ADR-0547: A member reached by reflection says so to Qodana

- **Status:** Accepted
- **Date:** 2026-10-03
- **Relates to:** [ADR-0498](0498-qodana-reads-a-reviewed-profile-and-a-bound-value-may-be-null.md),
  [ADR-0510](0510-publish-under-dev-goldberry.md),
  `docs/qodana-2026-10-02.md`

## Context

Three kinds of member have no caller in source. The woven `GoldberryCatalog`
calls each widget's `inflate` from bytecode that has no source,
and `RuntimeBinding` finds `@Bind` fields and `@Action` methods by reflection.
IntelliJ's `unused` inspection reads source, so to it they are dead.

ADR-0498 answered that with entry points in `.idea/misc.xml`: the two
annotations, and a `dev.goldberry.*` pattern for `inflate`. That worked until
the namespace moved (ADR-0510). From the first Qodana run after it, CI reported
123 of these members: 73 `inflate`, 22 `@Bind`, 28 `@Action`. The cause was not
found. It is not the action's cache, the linter build, the imported project or
the profile, and the configuration is loaded: some `inflate` methods with no
caller are left alone. In `ShowcaseModel` the members that survive are exactly
the ones that also have a caller in Java, so in CI the annotations save nothing.
No local run could be had to bisect it (`docs/qodana-2026-10-02.md` has the
evidence).

`unused` is a weak warning, so the findings never failed the gate, but they made
up most of the "new" count on every run and hid the findings that are real.

## Decision

**Every catalogue `inflate`, and every `@Bind` and `@Action` member, carries
`@SuppressWarnings("unused")`.** That is the repository's existing rule for a
false positive (suppressed where it is, never in the profile), and a suppression
on the member is read by every IntelliJ, in CI and in the IDE, whatever happens
to `misc.xml`. The `@Markup`, `@Bind` or `@Action` beside it is the reason, so
no comment repeats it.

`ReflectiveEntryPointsTest` in build-logic holds the rule: it reads every
module's `src/main/java` as text and names the file and line of any such member
without the suppression. A new widget or a new binding is caught by `check`, not
by the next Qodana run.

The `misc.xml` entry points stay. The IDE still reads them, and they cost
nothing.

## Consequences

- 166 members gained one line each, 82 `inflate` and 84 bindings; `Field`'s
  existing `unchecked` became `{"unchecked", "unused"}`.
- Error Prone's `UnusedVariable` and `UnusedMethod` honour the same suppression.
  These members are reached by reflection, so that check had nothing to say about
  them.
- The public documentation of `@Markup`, `@Bind` and `@Action` does not ask
  applications to do the same. It is this repository's answer to its own
  analyser, and an application's IDE is its own business.
- If the entry points start working again, nothing needs undoing. The
  suppressions are redundant, not wrong.
