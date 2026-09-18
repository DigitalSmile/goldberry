/// Goldberry core: the platform-agnostic toolkit.
///
/// Everything above the backend SPI lives here -- the three trees (widgets,
/// elements, render objects), the CSS engine, the layout and text stacks, the
/// paint pipeline, and the semantics tree. See `docs/ARCHITECTURE.md` §2.
///
/// The `natives` module is required for the `sdl3` backend. It exports only its
/// wrapper packages, so nothing here can reach a raw `MemorySegment` even by
/// accident -- the boundary in §3.1 is the module graph, not a convention.
module io.github.digitalsmile.goldberry.core {
    // **Not `transitive`, and that is the end of ADR-0280** (ADR-0290). No type
    // of `:natives` appears in a signature this module exports, so nothing that
    // requires `:core` needs to be able to read it -- and `-Xlint:exports` under
    // `-Werror` is what says so, not a comment: adding `transitive` back and
    // naming a `BlendFont` in a public method fails the build at that method.
    //
    // The last three sites were `Font.shape` returning a `GlyphRun` (ADR-0282),
    // `Paragraph.measureFunction` returning a `MeasureFunction` (ADR-0279), and
    // `Frame.drawGlyphs` taking a `BlendFont` and a `BlendGlyphBuffer`. The third
    // was the stubborn one: the other two were *values* and a value can be
    // mirrored, where a font and a staged buffer are handles whose owner had to
    // move. `paint.GlyphPen` is where they live now.
    requires io.github.digitalsmile.goldberry.natives;

    // Named here rather than taken through :natives. Logging is not the native
    // layer's to lend: this module reaches for it directly, which is what makes
    // it possible to read the graph and see that :core does not depend on
    // :natives *for* it (ADR-0174). `transitive`, because :widgets logs too.
    requires transitive io.github.digitalsmile.goldberry.common;
    requires org.slf4j;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    ///
    /// `static`, because they are compile-time only: a consumer's runtime module
    /// path does not need them. `transitive`, because `@Nullable` appears on
    /// exported signatures — a consumer compiling against `stringProperty` has to
    /// be able to read the annotation on it, and `-Xlint:exports` says so in as
    /// many words (`docs/testing.md` §2).
    requires transitive static org.jspecify;

    exports io.github.digitalsmile.goldberry;

    // The fonts and icons that ship in this jar (§6.1, ADR-0033). Exported
    // because an application choosing a font source, or registering an icon
    // pack, needs to name what it is replacing.
    exports io.github.digitalsmile.goldberry.assets;

    // The emoji face is not in this jar (ADR-0384). OpenMoji is CC BY-SA and
    // wants visible attribution, so it ships as `goldberry-emoji` and arrives
    // through a service — the module system's own answer to "somebody may have
    // brought this", and the same mechanism a widget catalog uses (ADR-0131).
    uses io.github.digitalsmile.goldberry.assets.EmojiFont;

    // The CSS engine (ADR-0049): stylesheets, the cascade, and the ComputedStyle
    // a render object is styled by (§8, ADR-0004). Exported because loading a
    // stylesheet and choosing a theme are things an application does.
    exports io.github.digitalsmile.goldberry.css;

    // The engine's stages, each its own package (ADR-0172): the tokenizer and
    // parser that read a stylesheet, the selectors it is indexed by, the cascade
    // that picks a winner, and the value types a declaration resolves to.
    exports io.github.digitalsmile.goldberry.css.parse;
    exports io.github.digitalsmile.goldberry.css.select;
    exports io.github.digitalsmile.goldberry.css.cascade;
    exports io.github.digitalsmile.goldberry.css.value;

    // §1.2's contrast floors, and the audit that measures a theme against them.
    // Exported for the same reason the cascade is: §10 lets an application swap
    // every alias token, and a theme it wrote is one nothing else can check
    // (ADR-0241).
    exports io.github.digitalsmile.goldberry.css.contrast;

    // Asking a stylesheet whether the engine will do what it says (ADR-0257).
    // Exported for the reason the contrast audit is: §8's subset drops what it
    // does not know, deliberately and quietly, and an application's own sheet
    // had no way to find out.
    exports io.github.digitalsmile.goldberry.css.lint;

    // KDL 2.0 markup and the inflater registry (§9, ADR-0051). Exported
    // because an application registers its own widgets in the same registry the
    // built-ins use.
    exports io.github.digitalsmile.goldberry.kdl;

    // Hot reload of stylesheets and markup (§1, §8, ADR-0051).
    exports io.github.digitalsmile.goldberry.reload;

    // The widget and element trees (ADR-0004, ADR-0052). The declarative layer
    // an application writes in, and the persistent tree the cascade, focus and
    // state hang off.
    exports io.github.digitalsmile.goldberry.widget;

    // The widget layer, split by what each part does (ADR-0172): the attributes
    // markup fills in, the styling a widget declares about itself, and the roots
    // the toolkit supplies. `WidgetRenderer` stays beside `Element` on purpose --
    // it is the element tree's own paint pass and shares its style cache.
    exports io.github.digitalsmile.goldberry.widget.attr;
    exports io.github.digitalsmile.goldberry.widget.style;

    /// §1.7's role and name. Exported because the catalog implements it and a
    /// second catalog would have to as well — and because the AccessKit bridge,
    /// when it lands, reads it from outside :core.
    exports io.github.digitalsmile.goldberry.widget.semantics;
    exports io.github.digitalsmile.goldberry.widget.root;

    // Observable values and the paths markup binds to (§9, ADR-0062). Exported
    // because the properties are the application's: it declares them, writes to
    // them, and registers the paths a markup file may name.
    exports io.github.digitalsmile.goldberry.bind;

    // The registries an application fills in, and the machinery a woven or a
    // reflectively-bound model runs on (ADR-0172). Both are exported because the
    // weaver writes call sites into an application's own classes, and those call
    // sites have to be able to name what they call.
    exports io.github.digitalsmile.goldberry.bind.registry;
    exports io.github.digitalsmile.goldberry.bind.runtime;

    // The flexbox vocabulary a `Box` and a `ComputedStyle` are written in
    // (ADR-0279). Its own package rather than part of `css` or `paint`, because
    // both of those name it and neither owns it -- and because what it replaced
    // was `:natives`' own vocabulary, reaching applications through every widget
    // that returns a Box.
    exports io.github.digitalsmile.goldberry.layout;

    // Pointer input: hit testing against the painted frame, and the dispatch
    // that turns it into events, pseudo-classes and focus (§7, ADR-0054).
    exports io.github.digitalsmile.goldberry.input;

    // Input, by the part it plays (ADR-0172): the events a widget is handed, the
    // keyboard vocabulary an accelerator is written in, the hit-test snapshot the
    // router works off, and the interfaces a widget implements to hear any of it.
    exports io.github.digitalsmile.goldberry.input.event;
    exports io.github.digitalsmile.goldberry.input.key;
    exports io.github.digitalsmile.goldberry.input.hit;
    exports io.github.digitalsmile.goldberry.input.handler;

    // A tap of a modifier key — pressed and released with nothing in between,
    // which is what §8's "`Alt`-style keyboard activation" is and what a
    // `Shortcut` cannot be (ADR-0223). Its own package rather than a fifth type
    // in `input.key`, because it is a *gesture* over two events and the keyboard
    // vocabulary beside it is a value.
    exports io.github.digitalsmile.goldberry.input.tap;

    // Files dropped on a window (`docs/gaps.md` G35b, ADR-0330). Its own package
    // rather than a type in `input.event`, for `input.tap`'s reason: a drop is a
    // *gesture* the toolkit assembles out of a run of platform events, and what
    // an application is handed is the assembled value rather than any of them.
    exports io.github.digitalsmile.goldberry.input.drop;

    // The frame clock, the three easing curves, and the per-node animation
    // overlay CSS transitions run through (design-system.md §1.7, ADR-0067).
    // Exported because an application supplies the clock -- a test drives a
    // virtual one so a golden image can snapshot a mid-animation frame -- and
    // because `Easing` is named by a `transition` a stylesheet writes.
    exports io.github.digitalsmile.goldberry.motion;

    // A decoded image, and the PNG encoder that writes one back out (ADR-0283).
    // Its own package rather than a class in `paint`, because `paint` is not the
    // only thing that wants the value: a frame draws one, an offscreen render
    // produces one, and a clipboard will carry one -- and `paint` already depends
    // on `render`, so an Image in `paint` would have pointed that dependency both
    // ways.
    exports io.github.digitalsmile.goldberry.image;
    exports io.github.digitalsmile.goldberry.image.png;

    // A picture with more than one frame in it (ADR-0382). Its own package for
    // `image.png`'s reason: the *sequence* is a value with arithmetic of its own
    // — which frame is being shown, how long a pass takes, whether it ever stops
    // — and none of that needs a decoder, a frame or a clock.
    exports io.github.digitalsmile.goldberry.image.anim;

    // The GIF decoder (`docs/gaps.md` G35a, ADR-0329). Exported beside the PNG
    // encoder and for its reason: both are formats this toolkit owns outright
    // rather than links, and an application that has a reason to reach one
    // directly -- a thumbnail pipeline, a test fixture -- should not have to go
    // through `Image` to do it.
    exports io.github.digitalsmile.goldberry.image.gif;

    // Rendering a scene without a window (ADR-0284): a painter or a whole widget
    // tree into an `image.Image`. Its own package rather than part of `render`,
    // because `render` is the backend SPI underneath everything and this composes
    // the layers above it -- the element tree, the cascade, the render tree and
    // the paint pipeline.
    exports io.github.digitalsmile.goldberry.offscreen;

    // Icons: the bundled Lucide set and the SVG path reader that gets it onto a
    // Blend2D path (ADR-0043). Separate from `text` because an icon shares
    // nothing with the font chain except the context it is drawn into.
    exports io.github.digitalsmile.goldberry.icon;

    // Where HarfBuzz's shaping meets Blend2D's rasterizer (ADR-0034). The two
    // libraries know nothing of each other, and this package is what holds them
    // to the one thing they must agree on: the units a glyph position is in.
    exports io.github.digitalsmile.goldberry.text;

    // Editing text: the caret, the selection, the undo stack and where a caret
    // lands on a wrapped paragraph (ADR-0285). Its own package beside the shaping
    // it is arithmetic over, and exported because an application editing text on
    // a `canvas` -- a sticky, a label on a shape -- is the case the widget
    // catalogue cannot serve.
    exports io.github.digitalsmile.goldberry.text.edit;

    // The keyboard half of editing, on its own (ADR-0376): the one map all three
    // editors read, and the commands it produces. Exported for the same reason
    // the editor is -- an application editing text on a canvas takes its keys
    // through this -- and separate from the editor because it touches no text.
    exports io.github.digitalsmile.goldberry.text.edit.keys;

    // The font chain -- a face, a sized font, and the fallback list a
    // paragraph is shaped against -- separately from the paragraph itself
    // (ADR-0172). An application picks a font source; it does not lay out a
    // line by hand.
    exports io.github.digitalsmile.goldberry.text.font;

    // The OpenType tables the text stack reads for itself -- a face's colour
    // glyphs, and the directory that finds them (ADR-0393). Exported because a
    // face's contents are a question an application asks for the same reason it
    // asks `FaceCoverage` what characters are in one: an emoji picker deciding
    // whether it can show something in colour.
    exports io.github.digitalsmile.goldberry.text.font.sfnt;

    // How a string is split into the runs each face shapes -- the emoji slot,
    // as a value rather than as a promise (ADR-0393). Exported because an
    // application drawing text on a `canvas` does its own shaping, and doing it
    // without this would draw the boxes `docs/gaps.md` G49 reported.
    exports io.github.digitalsmile.goldberry.text.itemize;

    // What a line does when it does not fit -- `white-space` and
    // `text-overflow`, and the value that carries them together (ADR-0255).
    // Exported because both are §8 properties an application's stylesheet may
    // write, and because a widget building its own label box names the value.
    exports io.github.digitalsmile.goldberry.text.flow;

    // The backend SPI, and the one backend that needs no platform under it.
    // `sdl3` will live here too and will be what makes this module `requires`
    // the natives module; `headless` deliberately does not, so tests of
    // everything above the SPI need no native library at all (ADR-0019).
    // What this build of the platform layer can actually do (ADR-0325). Exported
    // because an application that follows the desktop has to be able to tell "the
    // desktop says nothing" from "this build cannot ask" -- the first is a
    // default and the second is a bug report (`docs/gaps.md` G32).
    exports io.github.digitalsmile.goldberry.platform;

    exports io.github.digitalsmile.goldberry.render.desktop;
    exports io.github.digitalsmile.goldberry.render.dialog;
    exports io.github.digitalsmile.goldberry.render.backend.headless;
    exports io.github.digitalsmile.goldberry.render.backend.sdl3;
    exports io.github.digitalsmile.goldberry.render.model;
    exports io.github.digitalsmile.goldberry.render.popup;
    exports io.github.digitalsmile.goldberry.render.tray;
    exports io.github.digitalsmile.goldberry.render.window;
    exports io.github.digitalsmile.goldberry.render.event;
    exports io.github.digitalsmile.goldberry.render;
    exports io.github.digitalsmile.goldberry.stats;
    // Geometry over a `paint.Path` that the rasterizer does not do for us
    // (ADR-0278): flattening a curve to straight segments, and cutting a path
    // into a dash pattern's on runs. Its own package rather than more static
    // methods on `Path`, because both are algorithms over a value rather than
    // things the value knows about itself -- and both are worth testing without
    // a frame.
    exports io.github.digitalsmile.goldberry.paint.geom;
    // What turns one `box-shadow` into the run of rounded-rectangle fills a
    // rasterizer with no blur can draw (ADR-0310): the band alphas, and the band
    // shapes. Its own package for `paint.geom`'s reason -- both halves are
    // arithmetic over values, neither needs a `Frame`, and both are wrong in
    // ways only a unit test notices.
    exports io.github.digitalsmile.goldberry.paint.shadow;
    // What a subtree actually draws, and therefore what the painter may skip
    // (ADR-0313). Its own package for `paint.geom`'s reason once more: it is
    // arithmetic over rectangles, it needs no `Frame`, and a culler that is
    // wrong by a pixel drops a row off the bottom of a list -- which is exactly
    // the kind of defect a unit test catches and a golden image does not.
    exports io.github.digitalsmile.goldberry.paint.cull;
    // What did not fit, and the one place that says so (ADR-0375). Its own
    // package rather than a class in `paint.tree`, because the *reporting* is
    // not a layout concern: the walk that finds an overrun needs the tree, and
    // everything after it -- what to call the box, how to phrase it, how not to
    // say it sixty times a second -- needs nothing but the two rectangles.
    exports io.github.digitalsmile.goldberry.paint.overflow;
    exports io.github.digitalsmile.goldberry.paint.tree;
    exports io.github.digitalsmile.goldberry.paint;
}
