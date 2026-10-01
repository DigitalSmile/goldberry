# ADR-0510: Publish under `dev.goldberry`

- **Status:** Accepted. Supersedes [ADR-0009](0009-publish-under-io-github-digitalsmile.md)
- **Date:** 2026-10-01
- **Relates to:** `docs/ARCHITECTURE.md` §15, `docs/releasing.md`,
  [ADR-0334](0334-central-is-fed-once-per-run.md),
  [ADR-0509](0509-goldberry-dev-is-the-landing-page-and-the-book-is-its-docs.md)

## Context

ADR-0009 put the Maven group and the base package under
`io.github.digitalsmile`, because that was the namespace Central would verify
against the GitHub account. It also warned that these are among the few names
that cannot change once a release has used them.

Since ADR-0509 the project has its own domain, `goldberry.dev`. Central verifies a
reverse-domain namespace with a DNS TXT record, so `dev.goldberry` is now
claimable as well. The project's name is then the root of every coordinate,
package and module, and the owner's account name is not.

No release has been cut. Only `-SNAPSHOT`s have reached Central, and they are
replaced on every push. This is the last point where the rename costs nothing
beyond the change itself.

## Decision

- The Maven group is `dev.goldberry` (`PublishedModules.GROUP`, and the two
  unpublished build tools, `:assets` and `:weaver`).
- The base package is `dev.goldberry`. Every package keeps its path beneath the
  root: `io.github.digitalsmile.goldberry.widget` is `dev.goldberry.widget`.
- JPMS module names follow: `dev.goldberry.{common,natives,core,widgets,html,emoji,gpu,media,example}`.
  `--enable-native-access=dev.goldberry.natives` (and `dev.goldberry.media`) is
  what an application now passes.
- Resource paths follow the packages (`/dev/goldberry/...`). Each module's
  native-image metadata moves to
  `META-INF/native-image/dev.goldberry/<artifact>/reachability-metadata.json`.
- **Artifact ids do not change.** `goldberry`, `goldberry-bom`, `goldberry-core`
  and the rest keep their names. That keeps jar file names unambiguous in a flat
  `lib/` directory, and a consumer changes only the group.
- The POM's `url` is `https://goldberry.dev`. SCM and the developer entry still
  point at the GitHub repository and account.

The rename was mechanical and covered every tracked file: sources, module
descriptors, build scripts, `ServiceLoader` registrations, the SpotBugs and
Qodana configuration, the book and `docs/`. Three things were left alone on
purpose:

- **ADR-0009 is kept verbatim** apart from its status line. ADR-0334's mentions of
  the `io.github.digitalsmile` namespace on Central are kept too, because they
  record what was set up at the time.
- **References to downstream applications** in `docs/gaps.md`
  (`io.github.digitalsmile.brd.*`, `io.github.digitalsmile.tessera.*`). Those
  packages belong to other repositories.
- The developer id `digitalsmile` and the GitHub URLs, which name the account and
  not the namespace.

## Alternatives considered

- **Keep `io.github.digitalsmile`.** It works and is already verified. But it ties
  every import in every application to an account name, and that can never be
  fixed after the first release.
- **Change the group but not the packages.** Group and package would then
  disagree, and the native-image metadata path, which is keyed by group, would
  disagree with the module names. Doing half saves nothing, because the packages
  are exactly what a later rename could not change.
- **Rename the artifacts as well (`dev.goldberry:core`).** It reads well in Gradle,
  but a bare `core-2026.1.jar` in an application's `lib/` directory is
  ambiguous. Artifact ids are a separate decision, and this one does not need it.
- **Relocation POMs under the old group.** Nothing has been released under
  `io.github.digitalsmile` to relocate from. Snapshots are not relocated.

## Consequences

- **Central needs the new namespace before the next snapshot can upload.**
  `dev.goldberry` has to be added in the Portal, verified with the TXT record it
  issues on `goldberry.dev`, and have snapshots enabled. Until then
  `publish.yml` is refused (`docs/releasing.md`, one-time setup). The old
  namespace's snapshots stay where they are and stop updating.
- Downstream applications that build against snapshots change their imports,
  their `module-info` `requires`, their `--enable-native-access` flags and their
  dependency group in one go. A search and replace of
  `io.github.digitalsmile.goldberry` → `dev.goldberry` and of the group
  `io.github.digitalsmile:goldberry` → `dev.goldberry:goldberry` covers it.
- System properties and logger names that were spelt with the full package
  (for example a logging configuration naming `io.github.digitalsmile.goldberry`)
  are now `dev.goldberry`.
- The palantir import order lists `dev.goldberry` as the project's own group, last,
  as `io.github.digitalsmile` was before.
