# Changelog

What changed in each release of Goldberry, newest first. Each section is the body of
that release's page on GitHub: the release workflow writes it into
[`.github/release-notes.md`](.github/release-notes.md) when the tag is pushed, and
refuses a tag whose version has no section here.

Versions are calendar versions, `YEAR.RELEASE[.PATCH]`
([Versions](https://goldberry.dev/docs/contributing/releasing.html#versions)).

<!--
A new release: add an `## Unreleased` section above the newest one and collect the
changes in it as they land on master. Before the tag, rename it to the version and
add the date. Leave out a heading that has nothing under it.

## 2026.3 — YYYY-MM-DD

One or two sentences on what the release is about.

### Added

- A new widget, module or API, and what it is for.

### Changed

- Behaviour that is different now, and what to do about it.

### Fixed

- What was wrong, as a user saw it.

### Removed

- What is gone, and what to use instead.
-->

## 2026.2 — 2026-10-02

Welcome to the first release of Goldberry.

Goldberry is a declarative desktop UI toolkit for Java 25: widgets as Java records
or KDL markup that reloads while the application runs, real CSS and flexbox, and
the same code on the JVM or compiled by GraalVM into one native binary, on Linux,
Windows and macOS. It is pure Java over native C libraries through the Foreign
Function & Memory API, with no JNI and no bundled web engine.

Everything is new, so there is no list of changes this time. The guide at
[goldberry.dev/docs](https://goldberry.dev/docs/) is the place to start, and
[the issues](https://github.com/DigitalSmile/goldberry/issues) are open for what you
find.
