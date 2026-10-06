# The guide states what is: neutral documentation

Working notes and status for the rewrite of the guide into neutral,
reader-facing prose. The rule is recorded in `docs/snapshot/adr/0567-…` and
the style guide in `docs/book.md`.

## The rule

The guide (`book/src`, every chapter `SUMMARY.md` lists except `status.md`
and `TODO.md`), the `README.md` and the guide sections parked in
`docs/snapshot/` describe the toolkit as it is. They do not describe how it
came to be. A reader who has never seen the repository must not be able to
tell, from the guide, that anything was ever different.

### What goes

| Pattern | Example | Becomes |
|---|---|---|
| A change narrated as a change | "The natives jar is a module now" | "The natives jar is a module" |
| Temporal words about the toolkit | "no longer", "now", "not yet", "still", "used to", "since", "earlier", "previously", "at the time", "so far" | the present state, with the word dropped |
| A former behaviour | "the four no longer derive one automatic name from their file names" | drop, or state the current behaviour only |
| A bug or failure story | "GraalVM 25.4 refused some of those entries outright and an application's build had to strip them" | drop; keep the current design in one sentence |
| A citation of the decision log | "([ADR-0540](https://github.com/…/adr/0540-…))", "ADR-0159 records the experiments" | drop the parenthesis or the sentence |
| A `Read more` section that lists records | "### Read more" followed by `- [ADR-0533: …](…)` | drop the whole section; keep any entry that links a guide chapter or a public document |
| A struck item and its resolution | "~~**No CI job.**~~ **Built** (…): every leg…" | one plain paragraph stating what is |
| Reviews, sweeps, batches, gaps, milestones, runs, dates | "the 2026-09-18 review", "G27", "GB-012", "M4 — GPU", "run 17", "2026-09-30" | drop; say the fact the sentence carried |
| Working documents | "`docs/gaps.md`", "`docs/gpu-plan.md`", "TODO.md says" | drop |
| The repository's tests as evidence, in reader chapters | "a test holds `dev.goldberry.widget` to the same closed-world list" | state the guarantee: "every widget class is in a closed list" |
| Plans and promises | "Revisit if native-image grows a way…", "will be", "is planned", "is not built yet" | state what is and is not, in the present tense, without a promise |
| Hedges about the toolkit's maturity | "built, unproven", "on one machine", "the first release" | drop |

### What stays

- Every fact about the toolkit: attributes, defaults, types, keys, limits,
  numbers, commands, file names.
- Every fenced code block, byte for byte. A `kdl` sample is inflated by a
  test; a date inside a sample is data, not narrative.
- Every HTML block: `<div class="gb-tabs">`, `<picture>`, `<img>`, `<div class="gb-shot">`.
- Every table's columns and rows, with only the prose in a cell changed.
- Every heading, exactly. The showcase links a card to every `##` and `###`
  heading of a widget or feature chapter, doc comments link
  `https://goldberry.dev/docs/<page>.html#<anchor>`, and chapters link one
  another by anchor. A heading that is itself narrative ("The natives jar is
  a module now") may be renamed only when a search of the repository for its
  anchor finds no user outside the chapter itself, and the rename is reported.
- Links to other chapters, to public standards, and to external software.
- A limitation. Where the toolkit does not do something the chapter says so
  in one sentence, in the present tense, and gives the reason in words when
  the sentence already carries it. Nothing is invented to fill the place of
  a dropped citation.
- In `contributing/`, the tests, gates, workflows and the decision log are
  the subject and are described as what they are. History still goes.

### Voice

The style in `docs/book.md` stands: a Java developer with a window to build,
short sentences, one idea each, active voice, present tense, no semicolons
joining clauses, no em dashes, no parentheses. A sentence that only existed
to narrate a change is removed rather than reworded. A paragraph that loses
all its sentences is removed. Nothing is padded to keep a length.

## Verification

For every file touched:

```sh
git diff -U0 -- <file> | grep -E '^[-+]#{1,6} '          # no heading changed (or a reported rename)
grep -nE 'ADR|adr/|~~|\bG[0-9]{1,2}\b|GB-0|docs/[a-z-]+\.md|20[0-9]{2}-[0-9]{2}-[0-9]{2}' <file>   # nothing outside a code block
grep -nwE 'now|no longer|not yet|used to|still|yet|previously|earlier|since|anymore' <file>  # each hit read; only non-temporal uses remain
```

Then the guards:

```sh
./gradlew :build-logic:test --tests '*BookTest*' --tests '*SourceDocsTest*'
python3 tools/book/guide_links.py
./gradlew :example:test --tests '*BookMarkupTest*' --tests '*BookSectionsTest*' --tests '*GuideChapterTest*' --tests '*GalleryDocsTest*' -Pgoldberry.skipNative=true
MDBOOK_OUTPUT__HTML__SITE_URL=/docs/ ~/bin/mdbook build book -d /tmp/_site/docs
```

## Open points

- `getting-started/first-native-application.md` resolves `dev.goldberry:goldberry-weaver` through the BOM, and `overview/architecture.md` says `:weaver` is not published. `weaver/build.gradle` applies no publishing plugin and the BOM does not list it. One of the two chapters is wrong about a fact, which this rewrite did not settle.
- `status.md` and `TODO.md` are untouched. A neutral version of either would be a different document.

## Status

| Chapter | State | Note |
|---|---|---|
| `introduction.md`, `overview/*`, `getting-started/*`, `applications.md`, `README.md` | done | 95 citations out. One row of the limitations table went: a release in progress is not a limitation. The catalogue count is 82, not 79 |
| `layout/*` | done | 13 citations and 10 `Read more` sections out |
| `components/index.md`, `text`, `buttons`, `choices`, `values` | done | 51 citations and 17 `Read more` sections out |
| `components/forms`, `panels`, `collections` | done | 45 citations and 19 `Read more` sections out |
| `components/navigation`, `menus`, `overlays`, `charts` | done | 47 citations and 16 `Read more` sections out; the record numbers in the charts Java sample's comments out |
| `components/drawing`, `content`, `media`, `gpu`, `docs/snapshot/*.md` | done | 57 citations and 12 `Read more` sections out. `## What is measured, and what is not yet` in gpu.md stays: the showcase links it |
| `performance/*`, `guide/markup`, `styling`, `design-system` | done | about 150 citations out; the `Record` column of six tables renamed to the fact it now carries; `Read more` kept where a chapter link remained |
| `guide/text`, `input`, `windows`, `testing`, `logging`, `writing-a-widget` | done | 78 citations out; three failure-table links repointed at chapters; the record number in the logback sample's comment out |
| `weaving.md`, `native.md`, `contributing/*` | done | 109 citations out. Renamed: `The natives jar is a module now` to `The natives jar is a module` (one link, updated), `What the trace no longer records` to `What the trace does not record` (no links), decisions.md `How the guide links a record` to `Where a record is linked from` (no links) |
| `status.md`, `TODO.md` | out of scope | Tracking pages: a status table and a list of what is not built. Their content is the development record by design. |
| `adr/` | out of scope | The decision log is written for the maintainer and is not built into the site. |
| `BookTest` guard | done | `BookTest.Neutral`: a chapter outside `status.md` and `TODO.md` cites no record and links none but the template, says "ADR" only under `contributing/`, strikes nothing through, and names no gap or working document outside `contributing/`. `Book.prose` reads the lines outside fences |
| `docs/book.md` style | done | The citation parenthesis and the `Read more` list are withdrawn; the rule is stated |
| `docs/snapshot/adr/0567` | done | Parked with the GB batch; the manifest lists it |
