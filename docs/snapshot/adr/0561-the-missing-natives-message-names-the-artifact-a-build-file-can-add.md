# ADR-0561: The missing-natives message names the artifact a build file can add

- **Status:** Accepted
- **Date:** 2026-10-05
- **Relates to:** the Gwent clone's issue list (GB-012),
  [ADR-0510](0510-the-namespace-is-dev-goldberry.md),
  `book/src/getting-started/installing.md`

## Context

The first run of a downstream application with `goldberry-gpu` on the class
path and no native library failed with:

> Add the goldberry-natives-linux-x64 artifact, or set -Dgoldberry.native.library.

No such artifact is published. The natives are one artifact,
`dev.goldberry:goldberry-natives`, with four classifiers, and the line a build
file needs is `runtimeOnly 'dev.goldberry:goldberry-natives::linux-x64'`, as
the installing guide says. The message was written when the jars were named by
hand and was never read against the published coordinates. A message at the
point of failure is the one piece of documentation a new user is certain to
read, and this one sent them to search Central for a name that is not there.

## Decision

`NativePlatform` answers `coordinate()`: the classifier jar's dependency
notation, `dev.goldberry:goldberry-natives::<classifier>`. The missing-library
error names that coordinate, says it is a runtime dependency, names the
classifier in words for readers of Maven's XML, and keeps the property
override. The message is built by one package-private method so a test can
assert what it says and that the old name is gone.

## Alternatives considered

- **Fix the string in place.** The same words would have been right, but the
  coordinate would have been typed a second time, apart from the classifier
  the platform already knows, and nothing would have checked it. The platform
  record is where the classifier contract lives; the coordinate belongs beside
  it.
- **Link the installing page.** The guide's URL would go stale before the
  coordinate does, and a stack trace is read offline.

## Consequences

- A first run without natives says the exact line to add.
- `NativePlatform.coordinate()` is one more string that has to agree with the
  publishing: the group from ADR-0510 and the artifact id in `natives/build.gradle`.
  The test pins all four classifiers.
- Nothing changes for a run that has the library.
