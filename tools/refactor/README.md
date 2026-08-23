# Package-move tooling

`move_package.py` moves Java types between packages and rewrites every reference
in the repository. It was written for
[ADR-0172](../../book/src/adr/0172-a-package-is-a-role-and-the-module-is-the-fence.md)
and is kept because the next package move will want it too.

```sh
python3 tools/refactor/move_package.py \
    --from io.github.digitalsmile.goldberry.css \
    --to   io.github.digitalsmile.goldberry.css.parse \
    --classes CssTokenizer,Token,TokenType,CssParser,CssSyntaxException
```

A package move is four edits, and IDEs and `sed` alike tend to do three:

1. the file moves and its `package` line changes;
2. every `import old.Type;` in the repository becomes `import new.Type;`;
3. every fully-qualified `old.Type` follows — in code **and** in the javadoc
   links this codebase uses heavily;
4. every file *left behind* in the old package gains an import, because it used
   the type without one while they shared a package — and every file that
   *moved* gains imports for the siblings it left behind.

It also re-anchors the `](../../../book/src/adr/…)` relative links in doc
comments, since a file that moved a package deeper is one `../` further from
`book/`, and it drops imports that have become same-package.

## What it does not do

- **`module-info.java`.** A new package needs an `exports` line, and whether it
  gets one is a decision, not a rename. Add it by hand.
- **Package names written as strings.** `ModelWeaver` and `CatalogWeaver` build
  `ClassDesc`s from text; the tool rewrites those literals when they are fully
  qualified, but a name assembled from a prefix constant is invisible to it.
  `WrittenNamesTest` in `:weaver` is what catches that.
- **Judgement.** It will happily move a class whose callers need its
  package-private members, and the compiler will then tell you. That error is
  the useful part: either the boundary is real and the member should be public
  and say why, or the split was wrong and the class goes back. Both happened
  during ADR-0172.

## Rules it does know

Imports are only wired between source sets that can see each other — `main` sees
`main`, `test` sees everything — so a main class never gains an import of a test
class because its javadoc named one.

Run `./gradlew build` after every move. The moves in ADR-0172 were eight commits
and each one is green.
