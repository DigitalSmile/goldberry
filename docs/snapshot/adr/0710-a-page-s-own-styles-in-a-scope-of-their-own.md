# ADR-0710: A page's own styles, in a scope of their own

- **Status:** Accepted, as W3.3 of the downstream gaps plan (Tessera's T-24,
  "HTML for mail bodies"), under the owner's D3 (a): no inline layer, so
  `style=` becomes a generated class per element plus a rule.
- **Date:** 2026-11-21
- **Relates to:** ADR-0602 (the global keywords: `revert` and `revert-layer`,
  refused in a scope), ADR-0603 (`display: none`, which a mail's preview line
  uses), ADR-0636 (emphasis in the italic face, which edits the same guide
  paragraph)

## Context

`html-view` parsed a page's `<style>` and `style=` into the model and applied
neither. The cascade a page was under was the application's stylesheets alone,
which is what made a help page follow the theme. Tessera shows mail, and a mail
is styled: a newsletter is a white card with coloured headings, a receipt is a
table with a yellow total cell, and a preview line is hidden with
`display: none`. Today Tessera cuts the HTML down to a subset on its server and
falls back to plain text.

Three things stood in the way.

1. **There was nowhere to put a page's rules.** A resolver is built once per
   stylesheet set and is the application's. D3 (a) ruled out an inline layer,
   a declaration slot on a widget.
2. **A mail's selectors are about the mail, not about the widgets it becomes.**
   The fold turns an anchor into a `button.link`, a paragraph into a row of
   words, flattens `tbody` away, and an inline element is not a node at all but
   a class on each of its words. `ul > li`, `td a` and `p:first-child` would
   match that tree wrongly or not at all.
3. **Colour.** A mail is designed on its own white. Its reader may be in the
   dark theme. Its author colours meet the theme's somewhere, and that was the
   open question of the plan row.

## Decision

**Core gets a scope.** `ScopedRules` (in `css.cascade`) is a list of rules that
apply to one subtree, and `StyleScope` (in `widget.style`) is the composition
node that carries them. `StyleElement.scopes()` answers the scopes a node is
under, nearest first. The element tree counts mounted scopes, so a tree with
none answers without walking up. The resolver adds a scope's matching rules to
the node's cascade. **A scoped declaration beats every normal declaration in
the window's sheets whatever its specificity**, as an author's sheet beats a
browser's, and loses to an `!important` one, which is how an application
insists. A nearer scope beats a farther one. The cascade key is
`important, scope, specificity, layer, sheet, order`. Specificity could not be
the tie-break across the two origins without making the order intransitive.

A scoped selector is **one compound**: a type, an id, classes, and a state
among `:hover`, `:active`, `:focus` and `:focus-visible`. No combinator, no
structural or functional pseudo-class, no pseudo-element, no condition, no
custom property, no `!important` and no `revert`. Each refused feature is one
the resolver indexes once per stylesheet set to know what to invalidate, and a
scope arrives after that index was built. A plain compound needs none of it,
and it cannot reach outside its subtree through an ancestor selector. A scope
rebuilt with other rules restyles its subtree (`Element.matchesDiffer`); one
rebuilt with equal rules keeps every cached style below it.

**`:html` matches the mail against the mail.** `html.style.DocumentStyles`
walks the document once, wrapping each element as a `StyleElement`
(`StyleNode`) so the toolkit's own `SelectorMatcher` runs on the document's
shape. Each element's declarations are cascaded: presentational attributes
(`bgcolor`, a `font`'s `color`) first, then the `<style>` rules by specificity
and order, then its `style=`. The result is minted as one generated class,
`html-author-N`, and one rule. Two elements that say the same thing share a
class. The fold puts the class on the widget the element became, and
`HtmlView.build` wraps the page in a `StyleScope` holding the rules, always,
empty or not, so a page whose styles come and go keeps its widgets and
selection. A document class that reads as a generated one (`author-2`) is not
forwarded, so a mail cannot borrow another element's rule.

An inline element's class carries its own declarations merged over those of
the inline elements round it, and a word carries the innermost one alone. Two
rules of equal weight never have to agree on one word.

**Only a safe subset is read.** `AuthorDeclarations` keeps colour, type size,
weight, style, spacing, alignment, decoration and case, padding, margin,
border, corners, `max-width`, `opacity`, `visibility` and `display: none`. It
refuses anything that fetches (`url()`, `image-set()`, any function but the
colour and arithmetic ones; `background` only as a colour), anything that
leaves the view (`position`, `z-index`, `transform`, `float`, a negative
margin), anything that reaches the theme (custom properties and `var()`),
anything that moves (`animation`, `transition`), `font-family`, and a value
holding a backslash, a brace or a semicolon. `!important` is dropped from a
declaration. `StyleText` cuts a `<style>` into rules and declarations by hand.
Every at-rule (`@media`, `@import`, `@font-face`, `@keyframes`) is skipped
whole. Each piece is parsed alone with the toolkit's strict parser, so one bad
rule or selector is dropped without its neighbours and without a warning in
the application's log. `:link` is read as an anchor, `:visited` drops its
selector, and a `<style media="print">` is not read.

**Colour, the open question: `AuthorStyles`, default `ADAPTED`.** The view
takes `IGNORED` (the page before this record), `THEME_COLOURS` (layout as
written, every colour the theme's, a border keeping its width and style),
`ADAPTED` and `AS_WRITTEN`. Markup spells them
`author-styles="ignored|theme-colours|adapted|as-written"`. Under `ADAPTED`:

- a background the mail paints is kept and becomes the ground its contents are
  checked against;
- a text colour on such a ground is kept at 4.5:1 or better, and replaced by
  dark (`#1f2328`) or light ink where it is not;
- an element that paints a ground and says nothing of its text gets ink that
  reads on it, unless the colour it inherits from the mail already does;
- a link on such a ground gets a blue that reads there, because the theme's
  link colour is chosen for the theme's surface;
- **a text colour with no ground of the mail's under it is dropped.** It would
  sit on the theme's surface, which is light or dark depending on the reader,
  and nothing at build time knows which;
- borders keep their colours.

The other option considered was to read the theme's surface and check every
colour against it. A widget's build has no colour tokens (`BuildContext.token`
answers numbers), and checking at paint time would put a contrast pass in the
renderer for one widget. The mail's own ground is known when the document is
folded, and on real mail it is nearly always painted.

## Consequences

- A page's own styles now apply by default. A help page that carried a stray
  `style=` changes; `AuthorStyles.IGNORED` restores the old rendering. The
  breaking-changes list says so, and also that `HtmlView`'s canonical
  constructor gained a component (the old five-argument one stays).
- An inline element's box is not a box: a `background` or `padding` on a
  `span` is drawn round each of its words. `:last-child` and `:nth-last-child`
  never match, because the document is matched in one pass and a node's later
  siblings do not exist yet. Both are written down in the code.
- A scope is a composition node, so a child combinator in the window's sheets
  does not see through it, as with every composition node. `html-view` already
  had two of them between its parent and its page.
- The cost is a walk of the document per build, with each element trying only
  the rules filed under its classes, id and type. A document with no styles
  produces `DocumentStyles.NONE` and an empty scope.
- The showcase's HTML sample replaced its "a `<style>` is applied by nothing"
  paragraph with a receipt in a card of its own white. `screen-html-{light,dark}`
  and `gallery-html` were taken again; `html-mail-{dark,light}` are new
  goldens.
- Tested by `ScopedRulesTest` (the subset, value equality, candidates, the
  cascade rank, states), `StyleScopeTest` (the subtree, no box, a rebuild with
  other rules, scopes nearest first), `StyleTextTest`, `AuthorDeclarationsTest`
  (kept and refused, presentational attributes, an injected `bgcolor`),
  `DocumentStylesTest` (the cascade order, matching on the document's tree,
  states, classes shared and merged, each colour rule) and
  `HtmlAuthorStylesTest` (the scope, `IGNORED`, no borrowed classes, markup,
  beating the application, losing to `!important`, one mail not reaching the
  next, adapted ink, inline colours, `display: none`, refusals).
