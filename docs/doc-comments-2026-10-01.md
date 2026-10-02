# Doc comments, 2026-10-01

Working notes and status for the rewrite of every doc comment and comment in
the repository: each one explains the object to a reader of the published
javadoc, links the chapter of the guide that covers it, and no longer cites the
decision log. The decision is [ADR-0518](../book/src/adr/0518-a-doc-comment-explains-the-object-and-links-the-guide.md);
the house style is [Writing a doc comment](../book/src/contributing/doc-comments.md).

## What was found

On the morning of 2026-10-01, before any edit:

| Measure | Count |
| --- | --- |
| Java files citing a record | 1,556 |
| Lines citing a record, Java only | 5,176 |
| Lines citing a record, Java + Gradle + workflows | 5,367 |
| Of the Java lines, in `///` doc comments | 4,103 |
| Of the Java lines, in `//` comments | 902 |
| Of the Java lines, in string literals (`@DisplayName`, messages) | 118 |
| References to `docs/*.md` sections in Java | 955 |
| Published packages (`package-info.java` under `src/main/java`) | 229 |

## What holds it

- `build-logic/src/test/java/dev/goldberry/build/book/SourceDocsTest.java`:
  no record number in a Java, Gradle or workflow source; every guide link lands
  on a listed chapter and an existing heading; every published package links
  the guide. `GuideLink` parses the link; `GuideLinkTest` covers it.
- `tools/book/guide_links.py`: the same rules as a script, with
  `--anchors CHAPTER` to list the headings a link may name, runnable per file
  without Gradle.
- `./gradlew javadoc`: doclint over the published modules, which finds a
  `[Type]` link that does not resolve.

## The brief, as given to each batch

Edit comments, doc comments and the string literals that cite a record. Do not
change behaviour. Keep each file's comment syntax (`///` or `/** */`). Keep
every `///` line at 120 characters or fewer, counted from column one. Do not
add or remove imports; do not add a `[Type]` link to a type that is not
imported or in the same package (a name from another module goes in
backticks). Do not run Gradle: the batches run in parallel and the build is
run once, after.

For each file:

1. Remove every citation of the decision log. If the sentence carried its
   reason in the citation, read the record under `book/src/adr/` and write the
   reason as a plain clause. If the citation was history, drop it.
2. Replace `docs/<file>.md §N` references and quotations of a specification
   with the rule in plain words and, when a chapter covers it, a guide link.
3. For a public type in a published module, shape the class comment: what it
   is in one sentence; how to use it; how it works; `Read more:` with one link
   to the guide, anchored to a heading where one fits.
4. For a `package-info.java` in a published module, end with a guide link.
5. For a test, keep the account of what broke and how the test catches it,
   lose the citations, and make `@DisplayName` a sentence with no numbers.
   Assertion and exception messages say what failed and what to do.
6. Run `python3 tools/book/guide_links.py <your directories>` until it reports
   nothing.

## Batches

| # | Scope | Files citing | Status |
| --- | --- | --- | --- |
| A | `core/src/main`: `dev.goldberry` root, `module-info`, `assets`, `bind.*`, `css.*` | 54 | done, checker clean |
| B | `core/src/main`: `drive`, `frame`, `icon`, `image.*`, `input.*`, `kdl`, `layout`, `motion`, `offscreen` | 73 | done, checker clean |
| C | `core/src/main`: `paint.*`, `platform`, `reload`, `render.*` | 90 | done, checker clean |
| D | `core/src/main`: `stats`, `text.*`, `widget.*` | 67 | done, checker clean; three misplaced doc comments moved onto their members |
| E | `core/src/test`: root, `assets`, `bind`, `css`, `drive`, `frame`, `golden`, `icon`, `image`, `input`, `junit`, `kdl`, `layout`, `motion`; `core/src/testFixtures`; `core/src/jmh` | 110 | done in the first run |
| F | `core/src/test`: `offscreen`, `paint`, `platform`, `reload`, `render`, `stats`, `text`, `widget` | 91 | done: 110 files, checker clean |
| G | `widgets/src/main`: root, `module-info`, `controls.*`, `data.*` | 120 | done, checker clean; `§` refs in `data` cleared too |
| H | `widgets/src/main`: `core.*`, `form.*`, `markup` | 100 | done, checker clean; ~75 bare `§N` lines left for the sweep |
| I | `widgets/src/main`: `menu`, `nav.*`, `overlay.*`, `panel.*`, `shell.*`, `text` | 123 | done, checker clean |
| J | `widgets/src/test`: root, `arch`, `bind`, `controls`, `core`, `data` | 95 | done, checker clean; 158 bare `§N` references left for the sweep |
| K | `widgets/src/test`: `form`, `markup`, `menu`, `nav`, `overlay`, `panel`, `settle`, `shell`, `text` | 92 | done, checker clean; settle coupling verified |
| L | `natives/src/main`, `natives/src/test` | 180 | done, checker clean; `docs/gpu-plan.md` refs in `sdl/gpu` left for the sweep |
| M | `html`, `weaver`, `common`, `emoji`, `assets`, `gpu` (main and test) | 139 | done, checker clean |
| N | `media` (main and test), `example/src/test` | 130 | done, checker clean; 223 files |
| O | `example/src/main` | 53 | done, checker clean; showcase note copy changed, so the gallery goldens must be retaken |
| P | `build-logic` (main, test, groovy), every `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/libs.versions.toml`, `.github/workflows`, CMake and C sources, shell and Python tools, CSS and KDL resources, `book/book.toml`, `book/theme`, `book/diagrams` | 38 + ~70 | done; one PR-body string in `release.yml` fixed afterwards |

Left alone on purpose: `licenses/*.txt` and `media/src/ffmpeg-sources/README.txt`
are notices; `config/qodana/baseline.sarif.json` is a tool's own output;
`core/src/test/resources/.../libqrencode-vectors.txt` is a fixture; the
`reachability-metadata.json` files are read by native-image, and their
`comment` fields are checked by batch P.

## The first run, and the second

The first run of batches A to M was cut off by an API session limit after
about forty minutes, with batch F finished and the rest between a third and
nine tenths done: 2,125 of the 5,367 citing lines were gone, 747 files were
edited, every module still compiled, and no file had been truncated. Core's
tests (batch E) turned out to be complete as well. The other batches were
relaunched as fresh agents scoped to what the checker still reported, with C
split into C1 (`paint.*`) and C2 (`platform`, `reload`, `render.*`) and I
into I1 (`menu`, `nav.*`, `overlay.*`, `shell.*`, `text`) and I2 (`panel.*`).

Removed along the way: `widgets/.../form/textarea/TextAreaBox.java.orig`, a merge-tool
backup committed by accident with the namespace rename, three lines behind the real file
and full of citations the formatter never saw.

## What the formatter does to a `///` line

Two files came back from `spotlessApply` with a doc line split in two and the
second half no longer a comment, although every line was at or under 120
columns. Six runs over a scratch file placed the rule: palantir-java-format
2.97 leaves a top-level `///` block alone up to 120 columns, but reformats a
block inside a type as javadoc and wraps any line longer than 120 minus its
indent (116 at four spaces, 112 at eight, 108 at twelve), and the half it
pushes down loses its `///`. A line that is one backtick code span is never
broken, which is why four 119- and 120-column C signatures in `:media` have
always passed. `tools/book/guide_links.py` and the house style now say so.

## After the batches

- [x] `python3 tools/book/guide_links.py` reports nothing (one pre-existing code-span line in `AvUtilCalls` aside).
- [x] `./gradlew spotlessApply`, then `./gradlew compileJava compileTestJava`: clean.
- [x] `./gradlew javadoc`: every published module passes doclint. `:assets`, `:example`
      and `:weaver` are not published, have no doclint gate, and fail on `[Type]`
      links they have always had; one new one in `IconsScreen` was fixed.
- [x] `./gradlew -p build-logic test`: green, `SourceDocsTest` included.
- [x] `./gradlew --continue test`: every module green except three real failures and
      the goldens. `ButtonTest` refuses a `#` in the base stylesheet, so the guide
      anchors in `controls.css` comments lost their fragment; `TextRankTest` looks for
      the words "type rank" in the error, so the message says them again; the shader
      manifest holds a hash of each `.hlsl` source, so the three shader comments were
      restored from HEAD rather than recompiling the shaders (they still name
      `docs/gpu-plan.md`). `:media:testWithoutGpu` failed three ways beside the other
      suites and passed alone; the GPU lane passes on lavapipe.
- [x] `:example:test`: 14 gallery goldens and 14 book pictures changed with the
      rewritten notes; checked one diff by eye (text and the reflow under it only) and
      retook them with `-Dgoldberry.golden.update=true`.
- [x] `./gradlew check -x test`, `spotlessCheck`, `checkMarkdown`, `-p build-logic check`: green.
- [ ] Still open for review: `core/.../paint/tree/RenderTree.java` `damage()` comment was reworded
      from "a backend promise it does not currently make" to "only correct where
      the backend promises it"; check against the backends.
- [x] Follow-up sweep as four agents: S1 (widgets tests, controls/core/data) done, 47 files; S2 (widgets tests, panel/form) done, 44 files; S3 (the rest of the Java) done, 71 files, with ISO/IEC 18004, H.264, SVG and licence sections kept and named; S4 (CSS, KDL and the tool scripts) done, 22 files, 437 references. Two `§` inside showcase strings, two record numbers in `logback.xml`, the `NOTICE` and `THIRD-PARTY-NOTICES.md` citations and two git configuration comments were cleaned by hand afterwards; `README.md` keeps its two links, which point at records on purpose (measured after round two: 711 `§` lines in 291 Java files,
      111 `docs/*.md` references in 80, 45 gap ids `G<n>`; 237 of the files are
      in `:widgets`): bare `§N` section references and quoted spec sentences that
      the checker does not flag (batch K found several in `widgets/src/test/.../form`;
      batch B left `input/handler/Anchored.java` and `kdl/KdlSyntaxException.java`).

## The result

| Measure | Before | After |
| --- | --- | --- |
| Lines citing a record, Java + Gradle + workflows | 5,367 | 0 outside `DecisionLogTest` and `SourceDocsTest` |
| Java files citing a record | 1,556 | 0 |
| Section signs in Java that are not a public standard's | 711 | 0 |
| `docs/*.md` named in Java | 955 | 0 |
| Guide links in Java | 0 | 1,225 in 1,152 files |
| Published packages linking the guide | 0 of 229 | 229 of 229 |
| Files changed | | 2,103 |

Done by sixteen batch agents, sixteen resumed after a rate limit, four sweep
agents, and a hand pass over what the checker could not see: the notices, the
git configuration, the analysis exclusions, two showcase strings and the
shader sources.
