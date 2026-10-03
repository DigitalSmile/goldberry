# Contributing

<p class="gb-lede">Goldberry is Apache 2.0 with no contributor agreement, and every change passes the same gates whether it comes from a maintainer or from a stranger.</p>

A change is a pull request against `master` on [GitHub](https://github.com/DigitalSmile/goldberry). This chapter says what the repository expects of one. The five chapters after it say how to build, where things live, what the tests check, how a release goes out, and how a decision is written down.

## The licence

The code is under the [Apache License 2.0](https://github.com/DigitalSmile/goldberry/blob/master/LICENSE). There is no CLA to sign. A pull request is a contribution under that licence, and `LICENSE` and `NOTICE` ship inside every jar under `META-INF`.

Third-party software is disclosed in [`THIRD-PARTY-NOTICES.md`](https://github.com/DigitalSmile/goldberry/blob/master/THIRD-PARTY-NOTICES.md) and `licenses/`, and the two are held together by a task:

```sh
./gradlew checkLicenses
```

A dependency named in one and not the other fails the build. The reasoning is in [ADR-0015](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0015-licensing-and-third-party-disclosure.md).

## What a change passes

`./gradlew check` is the gate. CI runs the same thing on Linux, macOS and Windows, and a pull request is green only when all three are.

| Gate | What it holds | Fixed by |
|---|---|---|
| Formatting | Every Java file matches palantir-java-format | `./gradlew spotlessApply` |
| Error Prone and NullAway | `src/main` compiles with no finding, and every package is `@NullMarked` | Reading the finding |
| PMD | The hand-picked rules in `config/pmd/ruleset.xml` | Reading the finding |
| SpotBugs | Reports only. `build/reports/spotbugs/main.html` per module | Nothing yet |
| Tests, twice | The suite passes bound reflectively and again woven | The test, or the code |
| Goldens | Every reference image still matches what the code draws | `./gradlew blessGoldens`, then review the diff |
| Export list | Every symbol bound in Java is exported, and nothing exported is unbound | Editing `goldberry.symbols` |
| `package-info` | Every package has one, with a doc comment and `@NullMarked` | Writing it |
| Javadoc | A `[link]` in a published module resolves | Fixing the link |
| Markdown | No trailing whitespace, a final newline | `./gradlew formatMarkdown` |
| Licences | `THIRD-PARTY-NOTICES.md` and `licenses/` agree | Editing both |

[Tests and gates](testing.md) says what each one runs and why. The record behind the format-and-analysis tier is [ADR-0497](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0497-every-package-says-what-it-is-and-is-null-marked.md), and the record behind the two test runs is [ADR-0155](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md).

> [!TIP]
> Run `./gradlew spotlessApply check` before pushing. The formatter is the one gate that fixes itself.

## A widget arrives whole

A new widget is not done when it draws. `docs/testing.md` §5 says what it arrives with, and the rule is before review, not after:

1. a row in the widget specification, in the `core-widgets.md` format;
2. a row of design-system metrics;
3. a card in the showcase gallery, on the screen of the chapter that documents it, linking that section;
4. semantics assertions, so the sweep finds a role and a name;
5. golden images, in both themes and at both densities.

The gallery is the demo, the visual-regression corpus and the accessibility sweep at once, so a widget that is not in it is not tested. The gallery has a screen per chapter of this guide and a card per section, and `GalleryDocsTest` fails on a section with no card, a card with no link, or a link to a heading that does not exist. [Writing a widget](../guide/writing-a-widget.md) walks through the code. The sweeps that read the registry are in [Tests and gates](testing.md#accessibility-sweeps).

## Decisions are recorded

A change that chooses between designs gets an architecture decision record beside the code, in the same pull request. The log is the last part of this book, and [ADR-0001](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0001-record-architecture-decisions.md) says why it exists. [Recording a decision](decisions.md) says how to write one and what the build checks about it.

## The chapters

<div class="gb-cards">
<a class="gb-card" href="building.html"><strong>Building from source</strong><span>The JDK, the native superbuild, the build properties, the showcase and the native image, and what each platform needs.</span></a>
<a class="gb-card" href="repository.html"><strong>Repository layout</strong><span>The module map, the package rule, where the documents and the tools live, and the tests that guard against drift.</span></a>
<a class="gb-card" href="testing.html"><strong>Tests and gates</strong><span>The test kinds, what check runs, blessing goldens, the static analysis tiers and the CI matrix.</span></a>
<a class="gb-card" href="releasing.html"><strong>Releasing</strong><span>Calendar versions, snapshots on every push, the release checklist, a patch, and the showcase binaries.</span></a>
<a class="gb-card" href="decisions.html"><strong>Recording a decision</strong><span>When a change needs a record, the template, numbering, status, supersession and the house style.</span></a>
<a class="gb-card" href="doc-comments.html"><strong>Writing a doc comment</strong><span>What a comment says, how it links this guide, which chapter a package links, and why it never cites a record.</span></a>
</div>

## Where to ask

Open an issue at <https://github.com/DigitalSmile/goldberry/issues>. A bug report is most useful with the platform, the display scale, and the start-up timeline the toolkit logs at `TRACE`. [Logging and diagnostics](../guide/logging.md) says how to turn that on.

[Status](../status.md) says what is built, and [TODO](../TODO.md) says what is deferred and why. A question that one of those answers is answered there first.
