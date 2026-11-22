# Spell checking

Goes into `book/src/components/forms.md` as a new `## Spell checking` section,
after `## \`text-area\`` and before `## \`field\``. It is not a widget, so
`BookTest` wants no catalogue row. `BookMarkupTest` inflates the `kdl` block
below while it is here. Once the section is in the book, the showcase's
`SpellingCard` doc link can switch from `#text-input` to `#spell-checking`.

---

## Spell checking

A `text-input`, a `text-area` or a `rich-input` draws a wavy line under a
word its dictionary does not know, when it is told to check:

```kdl
column spellcheck=#true lang="en-US" {
  text-input placeholder="A title"
  text-area rows=4 placeholder="A note"
  text-input password=#true placeholder="Never checked"
}
```

`spellcheck` and `lang` go on any node and pass down to the fields inside it,
as they do in HTML. A field's own `spellcheck=#false` wins over its parents'.
Nothing is checked unless something says `spellcheck=#true`, and a password
never is. In Java, every widget has `.spellcheck(true)` and
`.lang(Locale.UK)`.

### Dictionaries

No dictionary ships with the toolkit, because their licences differ from one
language to the next. Add `goldberry-spell` to the application and load the
dictionaries it needs, in the common `.aff` and `.dic` format that office
suites and browsers use:

```java
SpellCheckers.load(Locale.US, Path.of("dict/en_US.aff"), Path.of("dict/en_US.dic"));
SpellCheckers.load(Locale.of("ru"), Path.of("dict/ru_RU.aff"), Path.of("dict/ru_RU.dic"));
SpellCheckers.defaultLanguage(Locale.US);
```

A field checks with the dictionary for its `lang`. A dictionary for `en_US`
also answers `en` and `en_GB` when there is no closer one. A field with no
`lang` uses `SpellCheckers.defaultLanguage`, or the only dictionary loaded
when there is just one. A language with no dictionary checks nothing. A
dictionary can be loaded from the application's resources as two streams.
Loading `en_US` takes a moment, so load it away from the UI thread with
`Goldberry.async` and the fields check as soon as it arrives.

The fields check the lines they show away from the UI thread, and remember
each line's answer by its text, so typing costs one line's check. A word is
not flagged while the caret is in it or just after it: it is still being
typed. In a `rich-input`, code, links and mentions are not checked.

### The menu

A right-click on a flagged word opens the field's menu on it, with up to five
suggestions above Cut, Copy and Paste. Then come **Add to dictionary** and
**Ignore**. Choosing a suggestion replaces the word as one step of undo, and
in a `rich-input` it keeps the word's bold or italic. **Ignore** lasts as long
as the field. **Add to dictionary** adds the word to the language's personal
dictionary, and every field in that language stops flagging it. The menu key
opens the same menu on the word at the caret.

The personal dictionary lives in memory. To keep it between runs, save it
yourself:

```java
var personal = SpellCheckers.personal(Locale.US);
personal.addAll(Files.readAllLines(saved));
personal.onChange(() -> Files.write(saved, personal.words()));
```

The menu's words come from the toolkit's labels, so a translation covers
them: `goldberry.addToDictionary`, `goldberry.ignoreSpelling`,
`goldberry.noSuggestions` and `goldberry.findingSuggestions`.

### How it looks

The line is `::spelling-error`, which takes `color`, `background` and the
`text-decoration` properties:

```css
::spelling-error           { text-decoration: wavy underline var(--gb-danger) }
text-area::spelling-error  { text-decoration: dotted underline var(--gb-warning) }
```

The first rule is the theme's. The line keeps its rhythm across a word that is
half bold, and the word keeps its own colour unless the rule gives it one.
