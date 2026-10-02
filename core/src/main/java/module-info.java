/// Goldberry core: the platform-agnostic toolkit.
///
/// Everything above the backend SPI lives here: the three trees (widgets,
/// elements, render objects), the CSS engine, the layout and text stacks, the
/// paint pipeline, and the semantics tree.
///
/// The `natives` module is required for the `sdl3` backend. It exports only its
/// wrapper packages, so nothing here can reach a raw `MemorySegment` even by
/// accident: the native boundary is the module graph, not a convention.
///
/// `@SuppressWarnings("module")` for the one qualified export to a module built
/// after this one, `render.composite` to `:gpu`, which javac cannot find while
/// compiling `:core` and warns about. `:natives` does the same, for the same
/// reason; the export still works, because the name is resolved at run time.
///
/// Read more: [Architecture](https://goldberry.dev/docs/overview/architecture.html#the-modules).
@SuppressWarnings("module")
module dev.goldberry.core {
    // Not `transitive`: no type of `:natives` appears in a signature this module
    // exports, so nothing that requires `:core` needs to be able to read it.
    // `-Xlint:exports` under `-Werror` is what holds that, not a comment: adding
    // `transitive` back and naming a `BlendFont` in a public method fails the
    // build at that method. The handles the rasterizer needs (a font, a staged
    // glyph buffer) live behind `paint.GlyphPen`; everything else crossing the
    // boundary is a value that can be mirrored.
    requires dev.goldberry.natives;

    // Named here rather than taken through :natives. Logging is not the native
    // layer's to lend: this module reaches for it directly, so the module graph
    // shows that :core does not depend on :natives for it. `transitive`, because
    // :widgets logs too.
    requires transitive dev.goldberry.common;
    requires org.slf4j;

    /// JSpecify's nullness annotations, for the null-marked packages.
    ///
    /// `static`, because they are compile-time only: a consumer's runtime module
    /// path does not need them. `transitive`, because `@Nullable` appears on
    /// exported signatures, and a consumer compiling against `stringProperty` has
    /// to be able to read the annotation on it.
    requires transitive static org.jspecify;

    exports dev.goldberry;

    // The fonts and icons that ship in this jar. Exported because an application
    // choosing a font source, or registering an icon pack, needs to name what it
    // is replacing.
    exports dev.goldberry.assets;

    // The emoji face is not in this jar. Noto Color Emoji is 5 MB that an
    // application which never draws an emoji should not carry, so it ships as
    // `goldberry-emoji` and arrives through a service: the module system's own
    // answer to "somebody may have brought this", and the same mechanism a widget
    // catalog uses to announce itself.
    uses dev.goldberry.assets.EmojiFont;

    // The CSS engine: stylesheets, the cascade, and the ComputedStyle a render
    // object is styled by. Exported because loading a stylesheet and choosing a
    // theme are things an application does.
    exports dev.goldberry.css;

    // The engine's stages, each its own package: the tokenizer and parser that
    // read a stylesheet, the selectors it is indexed by, the cascade that picks a
    // winner, and the value types a declaration resolves to.
    exports dev.goldberry.css.parse;
    exports dev.goldberry.css.select;
    exports dev.goldberry.css.cascade;
    exports dev.goldberry.css.value;

    // `@media`: the condition a rule applies under and the window facts it is
    // asked about. Exported because a rule carries one and a test or a tool
    // reading a parsed sheet meets it.
    exports dev.goldberry.css.media;

    // The design system's contrast floors, and the audit that measures a theme
    // against them. Exported for the same reason the cascade is: an application
    // may swap every alias token, and a theme it wrote is one nothing else can
    // check.
    exports dev.goldberry.css.contrast;

    // Asking a stylesheet whether the engine will do what it says. Exported for
    // the reason the contrast audit is: the CSS subset drops what it does not
    // know, deliberately and quietly, and an application's own sheet needs a way
    // to find out.
    exports dev.goldberry.css.lint;

    // KDL 2.0 markup and the inflater registry. Exported because an application
    // registers its own widgets in the same registry the built-ins use.
    exports dev.goldberry.kdl;

    // Hot reload of stylesheets and markup.
    exports dev.goldberry.reload;

    // The widget and element trees: the declarative layer an application writes
    // in, and the persistent tree the cascade, focus and state hang off.
    exports dev.goldberry.widget;

    // The widget layer, split by what each part does: the attributes markup
    // fills in, the styling a widget declares about itself, and the roots the
    // toolkit supplies. `WidgetRenderer` stays beside `Element` on purpose: it is
    // the element tree's own paint pass and shares its style cache.
    exports dev.goldberry.widget.attr;
    exports dev.goldberry.widget.style;

    /// A widget's role and accessible name. Exported because the catalog
    /// implements it and a second catalog would have to as well, and because an
    /// accessibility bridge reads it from outside :core.
    exports dev.goldberry.widget.semantics;
    exports dev.goldberry.widget.root;

    // Observable values and the paths markup binds to. Exported because the
    // properties are the application's: it declares them, writes to them, and
    // registers the paths a markup file may name.
    exports dev.goldberry.bind;

    // The registries an application fills in, and the machinery a woven or a
    // reflectively-bound model runs on. Both are exported because the weaver
    // writes call sites into an application's own classes, and those call sites
    // have to be able to name what they call.
    exports dev.goldberry.bind.registry;
    exports dev.goldberry.bind.runtime;

    // The flexbox vocabulary a `Box` and a `ComputedStyle` are written in. Its
    // own package rather than part of `css` or `paint`, because both of those
    // name it and neither owns it, and because it is the toolkit's vocabulary
    // rather than the layout engine's, reaching applications through every
    // widget that returns a Box.
    exports dev.goldberry.layout;

    // Pointer input: hit testing against the painted frame, and the dispatch
    // that turns it into events, pseudo-classes and focus.
    exports dev.goldberry.input;

    // Input, by the part it plays: the events a widget is handed, the keyboard
    // vocabulary an accelerator is written in, the hit-test snapshot the router
    // works off, and the interfaces a widget implements to hear any of it.
    exports dev.goldberry.input.event;
    exports dev.goldberry.input.key;
    exports dev.goldberry.input.hit;
    exports dev.goldberry.input.handler;

    // A tap of a modifier key: pressed and released with nothing in between,
    // which is what `Alt`-style keyboard activation is and what a `Shortcut`
    // cannot be. Its own package rather than a fifth type in `input.key`, because
    // it is a *gesture* over two events and the keyboard vocabulary beside it is
    // a value.
    exports dev.goldberry.input.tap;

    // Files dropped on a window. Its own package rather than a type in
    // `input.event`, for `input.tap`'s reason: a drop is a *gesture* the toolkit
    // assembles out of a run of platform events, and what an application is
    // handed is the assembled value rather than any of them.
    exports dev.goldberry.input.drop;

    // The frame clock, the three easing curves, and the per-node animation
    // overlay CSS transitions run through. Exported because an application
    // supplies the clock (a test drives a virtual one so a golden image can
    // snapshot a mid-animation frame) and because `Easing` is named by a
    // `transition` a stylesheet writes.
    exports dev.goldberry.motion;

    // A decoded image, and the PNG encoder that writes one back out. Its own
    // package rather than a class in `paint`, because `paint` is not the only
    // thing that wants the value: a frame draws one, an offscreen render produces
    // one, and a clipboard carries one. `paint` already depends on `render`, so
    // an Image in `paint` would have pointed that dependency both ways.
    exports dev.goldberry.image;
    exports dev.goldberry.image.png;

    // A picture with more than one frame in it. Its own package for `image.png`'s
    // reason: the *sequence* is a value with arithmetic of its own (which frame
    // is being shown, how long a pass takes, whether it ever stops) and none of
    // that needs a decoder, a frame or a clock.
    exports dev.goldberry.image.anim;

    // The GIF decoder. Exported beside the PNG encoder and for its reason: both
    // are formats this toolkit owns outright rather than links, and an
    // application that has a reason to reach one directly (a thumbnail pipeline,
    // a test fixture) should not have to go through `Image` to do it.
    exports dev.goldberry.image.gif;

    // The QR encoder, ISO/IEC 18004. Among the codecs and for their reason: a
    // specification with one right answer, small enough that owning it costs less
    // than linking it, and nothing in it names a widget. An application that
    // wants a code in a PNG rather than on screen reaches this directly.
    exports dev.goldberry.image.qr;

    // Rendering a scene without a window: a painter or a whole widget tree into
    // an `image.Image`. Its own package rather than part of `render`, because
    // `render` is the backend SPI underneath everything and this composes the
    // layers above it: the element tree, the cascade, the render tree and the
    // paint pipeline.
    exports dev.goldberry.offscreen;

    // Icons: the bundled Lucide set and the SVG path reader that gets it onto a
    // Blend2D path. Separate from `text` because an icon shares nothing with the
    // font chain except the context it is drawn into.
    exports dev.goldberry.icon;

    // Where HarfBuzz's shaping meets Blend2D's rasterizer. The two libraries know
    // nothing of each other, and this package is what holds them to the one thing
    // they must agree on: the units a glyph position is in.
    exports dev.goldberry.text;

    // Editing text: the caret, the selection, the undo stack and where a caret
    // lands on a wrapped paragraph. Its own package beside the shaping it is
    // arithmetic over, and exported because an application editing text on a
    // `canvas` (a sticky, a label on a shape) is the case the widget catalogue
    // cannot serve.
    exports dev.goldberry.text.edit;

    // The keyboard half of editing, on its own: the one map all three editors
    // read, and the commands it produces. Exported for the same reason the editor
    // is (an application editing text on a canvas takes its keys through this)
    // and separate from the editor because it touches no text.
    exports dev.goldberry.text.edit.keys;

    // A long text shaped one hard line at a time. Its own package beside the
    // paragraph it is built out of, because it answers a different question: a
    // paragraph is a label, and this is a document somebody is typing into.
    // Exported for the same reason `text.edit` is: an application editing text on
    // a `canvas` needs it, and `text-area` is only the first caller.
    exports dev.goldberry.text.document;

    // The font chain (a face, a sized font, and the fallback list a paragraph is
    // shaped against) separately from the paragraph itself. An application picks
    // a font source; it does not lay out a line by hand.
    exports dev.goldberry.text.font;

    // The OpenType tables the text stack reads for itself: a face's colour
    // glyphs, and the directory that finds them. Exported because a face's
    // contents are a question an application asks for the same reason it asks
    // `FaceCoverage` what characters are in one: an emoji picker deciding whether
    // it can show something in colour.
    exports dev.goldberry.text.font.sfnt;

    // How a string is split into the runs each face shapes, with the emoji slot
    // as a value rather than as a promise. Exported because an application
    // drawing text on a `canvas` does its own shaping, and doing it without this
    // would draw boxes where the emoji should be.
    exports dev.goldberry.text.itemize;

    // What a line does when it does not fit: `white-space` and `text-overflow`,
    // and the value that carries them together. Exported because both are
    // properties an application's stylesheet may write, and because a widget
    // building its own label box names the value.
    exports dev.goldberry.text.flow;

    // The backend SPI, and the one backend that needs no platform under it.
    // `sdl3` is what makes this module `requires` the natives module; `headless`
    // deliberately does not, so tests of everything above the SPI need no native
    // library at all. `platform` says what this build of the platform layer can
    // actually do. Exported because an application that follows the desktop has
    // to be able to tell "the desktop says nothing" from "this build cannot ask":
    // the first is a default and the second is a bug report.
    exports dev.goldberry.platform;

    exports dev.goldberry.render.desktop;
    exports dev.goldberry.render.dialog;
    exports dev.goldberry.render.backend.headless;
    exports dev.goldberry.render.backend.sdl3;
    exports dev.goldberry.render.model;
    exports dev.goldberry.render.popup;
    exports dev.goldberry.render.tray;
    /// The web view's page handle and what opens one.
    ///
    /// Exported like `render.tray` beside it, and for the reason that one is:
    /// what an application holds is a handle to something the platform draws, and
    /// `:widgets` puts the door on top of it.
    exports dev.goldberry.render.web;
    exports dev.goldberry.render.window;
    /// The compositor seam: what the sdl3 backend asks of `:gpu` to present a
    /// window through the GPU, found by `ServiceLoader` below. Exported to `:gpu`
    /// alone, because its signatures name `:natives`' window handle and nothing in
    /// it is for an application.
    exports dev.goldberry.render.composite to
            dev.goldberry.gpu;

    uses dev.goldberry.render.composite.Compositor;
    exports dev.goldberry.render.event;
    exports dev.goldberry.render;
    // The system clipboard's SPI and the `text/uri-list` it carries files in.
    exports dev.goldberry.render.clipboard;
    exports dev.goldberry.stats;
    // Geometry over a `paint.Path` that the rasterizer does not do for us:
    // flattening a curve to straight segments, cutting a path into a dash
    // pattern's on runs, and mapping one through an affine transform. Its own
    // package rather than more static methods on `Path`, because all three are
    // algorithms over a value rather than things the value knows about itself,
    // and all three are worth testing without a frame.
    exports dev.goldberry.paint.geom;
    // What turns one `box-shadow` into the run of rounded-rectangle fills a
    // rasterizer with no blur can draw: the band alphas, and the band shapes. Its
    // own package for `paint.geom`'s reason: both halves are arithmetic over
    // values, neither needs a `Frame`, and both are wrong in ways only a unit
    // test notices.
    exports dev.goldberry.paint.shadow;
    // What a subtree actually draws, and therefore what the painter may skip. Its
    // own package for `paint.geom`'s reason once more: it is arithmetic over
    // rectangles, it needs no `Frame`, and a culler that is wrong by a pixel
    // drops a row off the bottom of a list, which is exactly the kind of defect a
    // unit test catches and a golden image does not.
    exports dev.goldberry.paint.cull;
    // What did not fit, and the one place that says so. Its own package rather
    // than a class in `paint.tree`, because the *reporting* is not a layout
    // concern: the walk that finds an overrun needs the tree, and everything
    // after it (what to call the box, how to phrase it, how not to say it sixty
    // times a second) needs nothing but the two rectangles.
    exports dev.goldberry.paint.overflow;
    exports dev.goldberry.paint.tree;
    // The pen: width, caps, joins and dashes. Values with no native handle, for
    // `css.value`'s reason.
    exports dev.goldberry.paint.stroke;
    exports dev.goldberry.paint;
}
