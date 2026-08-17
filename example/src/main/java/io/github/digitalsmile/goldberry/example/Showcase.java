package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.text.Fonts;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.Widgets;
import io.github.digitalsmile.goldberry.widgets.Button;
import io.github.digitalsmile.goldberry.widgets.Checkbox;
import io.github.digitalsmile.goldberry.widgets.Radio;
import io.github.digitalsmile.goldberry.widgets.Progress;
import io.github.digitalsmile.goldberry.widgets.Slider;
import io.github.digitalsmile.goldberry.widgets.Spinner;
import io.github.digitalsmile.goldberry.widgets.RadioGroup;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.widgets.Toggle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Goldberry's showcase.
///
/// A window whose contents are a **widget tree**, styled by the cascade and
/// driven by the input router — the toolkit as an application actually uses it.
/// It was hand-built `Box`es until the widget model existed; the boxes are still
/// down there, one layer below, built by `WidgetRenderer` from the element tree
/// every frame.
///
/// What it exercises, and why each is here rather than only in a test:
///
/// - **The three trees.** Widgets are values, the element tree persists across
///   rebuilds, the box tree is materialized per frame (ADR-0052, ADR-0053).
/// - **`setState`.** Every button ends in one, and the element tree reconciles
///   the new description against the old — so the focus ring stays where it was
///   through a rebuild that replaced every widget in the window.
/// - **The cascade, with a theme in it.** The theme button swaps one stylesheet
///   in the `THEME` layer; everything restyles, including rules that name no
///   colour (§10).
/// - **Input, end to end.** Hover, press, click, focus, `Tab`, `Space`/`Enter`,
///   and a `Ctrl+T` accelerator — through a router fed by the frame that was
///   painted rather than a fresh layout (ADR-0054, ADR-0058).
/// - **A cursor and an icon in a box**, which is what makes
///   `SDL_CreateSystemCursor` and the icon-as-a-box path run outside a unit test
///   (ADR-0043, ADR-0057).
///
/// It is also where logging is configured — `logback.xml` beside this class,
/// because binding a logging implementation is an application's decision and
/// never a library's (ADR-0023).
///
/// Run it with `./gradlew run` from the repository root, or build the
/// self-contained image with `./gradlew :example:showcaseImage` (ADR-0048).
public final class Showcase {

    private static final Logger LOG = LoggerFactory.getLogger(Showcase.class);

    /// The showcase's own stylesheet: the layout of *this* window, and nothing
    /// about how a button looks.
    ///
    /// In the `APPLICATION` layer, so it wins over the toolkit's base rules —
    /// and every colour in it is a `var(--gb-*)`, which is what lets the theme
    /// button work without this file knowing a theme exists.
    private static final String STYLES = """
            /* `color` on the root, so nothing in this window can resolve to
               ComputedStyle.INITIAL's black. That default is deliberate --
               ADR-0066 keeps it unthemed so a missing stylesheet looks missing --
               which means an application has to say what its text colour is
               somewhere, and the root is the one place that covers everything.
               A control gets away with saying nothing because `controls.css` sets
               `color` on `checkbox`, `radio`, `toggle` and `slider` themselves; a
               bare `text` does not. */
            #root      { flex-direction: column; background: var(--gb-bg);
                         color: var(--gb-text) }

            #bar       { height: 44px; padding: 0 16px; gap: 12px;
                         align-items: center; background: var(--gb-surface) }
            #title     { color: var(--gb-text) }

            #body      { flex-grow: 1; padding: 16px; gap: 16px }
            #sidebar   { width: 30%; padding: 12px; gap: 8px;
                         flex-direction: column; background: var(--gb-surface) }
            #content   { flex-grow: 1; padding: 16px; gap: 16px;
                         flex-direction: column; background: var(--gb-surface-2) }
            #prose     { color: var(--gb-text) }
            #actions   { gap: 8px; align-items: center }
            /* §1.3's 8 for related controls. It was 4, which is tighter than the
               design system's own scale and which the progress bar was the first
               thing to show: a 4px line sitting 4px from a control above it and a
               spinner below it reads as one crowded lump rather than as three
               things. */
            #options   { flex-direction: column; gap: 8px }
            /* The bar and the spinner are one group and the options above them
               are another, so §1.3's 16 separates them -- as a `padding-top` on
               top of the column's 8, because a gap is one number for every child
               and this is a boundary rather than a spacing. */
            #task      { flex-direction: column; gap: 8px; padding-top: 8px }
            /* The spinner and its label are a glyph and its text, so they take the
               same 8px gap and the same centred alignment a `checkbox` gives its
               own -- and without `align-items` the label would stretch to the
               row's height and sit off the ring's centre. */
            #busy      { gap: 8px; align-items: center }
            /* `body`, not `caption`: this is a status line beside a control, which
               is the line §1.4 draws -- `caption` is for secondary text under one.
               Muted, because what it says is a state and not content. */
            #busy-label { color: var(--gb-text-muted) }
            .caption   { color: var(--gb-text-muted) }
            """;

    private static final String BODY_TEXT =
            "Yoga proposes a width and this paragraph answers with a height, which is the only"
                    + " thing a flexbox algorithm needs to know about text. The answer comes back"
                    + " through a Java method called from C returning a struct by value — the"
                    + " fiddliest thing the toolkit asks of the Foreign Function & Memory API, and"
                    + " the reason ADR-0017 exists.\n\n"
                    + "Drag the window's edge and the text re-wraps without being shaped again."
                    + " Click a button, or press Tab until one has the focus and then Space."
                    + " Ctrl+T switches the theme from the keyboard, and Ctrl+D switches the"
                    + " density — every control gets 4px shorter, and nothing in this file"
                    + " mentions a height.\n\n"
                    + "The theme radios are one Tab stop, not two: Tab reaches the group and the"
                    + " arrow keys move inside it. Tab away and back and you land on whichever is"
                    + " selected — including after Ctrl+T has changed it from outside the group,"
                    + " because the selection is the position rather than something remembered"
                    + " beside it.";

    private static final float ICON_SIZE = 20;

    private Showcase() {
    }

    // --- the widget tree -----------------------------------------------------

    /// The whole window, as one stateful widget.
    ///
    /// The icons are handed in rather than built here: a widget is a value that
    /// is rebuilt on every `setState` and thrown away, and an `Icon` owns native
    /// memory that has to be closed exactly once. `main` owns them
    /// ([ADR-0043](../../../../../../book/src/adr/0043-icons-are-stroked-paths.md)).
    /// @param status what the sidebar's last line follows — §9's `bind`, in its
    ///               Java form. Nothing in this tree writes it; the environment
    ///               probe does, from a virtual thread, and the line updates
    ///               itself (ADR-0062)
    record Screen(Icon palette, Icon plus, Observable<String> status, Runnable onChanged)
            implements Widget.Stateful {
        @Override
        public State<?> createState() {
            return new ScreenState();
        }
    }

    /// Everything the window remembers between builds.
    ///
    /// On the *element*, not on the widget — which is the point of ADR-0052:
    /// `Screen` is rebuilt constantly and could remember nothing, while this
    /// object is created once when the element is mounted and outlives every
    /// rebuild.
    static final class ScreenState extends State<Screen> {

        /// Which theme the window is showing, as a **value** rather than a field.
        ///
        /// A `Property<String>` because the radio group below binds to it, and a
        /// group's options are named by strings the document could have written.
        /// The theme was a plain field until a control needed to follow it, which
        /// is the ordinary way a value becomes bindable — nothing else about the
        /// theme changed.
        private final Property<String> themeName = Property.of("dark");

        private int clicks;

        /// What the checkbox below is bound to.
        ///
        /// A `Property` rather than a plain boolean, because that is what §9's
        /// `bind` reads and what ADR-0063's loop needs: the checkbox is handed the
        /// read-only half and cannot write to this, so the tick moves only when
        /// `toggleProse` moves it. Set the handler aside and the control stops
        /// working — which is the behaviour, not a bug.
        private final Property<Boolean> showProse = Property.of(true);

        /// The tri-state one, starting partial.
        ///
        /// A `Property<Checkbox.Value>` rather than a `Boolean`, because `MIXED`
        /// is a real state and a boolean cannot hold it. `Checkbox.resolved()`
        /// accepts either, so an application with an ordinary binary preference
        /// never has to import the enum — this one does, because it has three
        /// states to be in.
        private final Property<Checkbox.Value> partly = Property.of(Checkbox.Value.MIXED);

        /// §1.3's density preference, and it is a plain field rather than a
        /// `Property` because nothing in this tree binds to it — no control shows
        /// which density is on, the way the radio group shows the theme. It moves
        /// every control's height and is named by no widget, which is exactly
        /// what "token-conformant apps adapt with zero code" means (ADR-0074).
        private Density density = Density.REGULAR;

        /// Which theme the window is showing, read by `main` to choose the
        /// stylesheets. The state owns it because the controls that change it
        /// live in this tree.
        Theme theme() {
            return "light".equals(themeName.get()) ? Theme.NORD_LIGHT : Theme.NORD_DARK;
        }

        /// Which density the window is showing, read by `main` for the same
        /// reason and through the same door.
        Density density() {
            return density;
        }

        /// `Ctrl+D`. There is no button for it and deliberately so: a density is
        /// an application-wide user preference, so the showcase changes it the
        /// way an application would — from a menu or a settings screen, neither
        /// of which exists yet — rather than putting a control for it in the
        /// tree it is resizing.
        void toggleDensity() {
            changed(() -> density = density == Density.REGULAR ? Density.COMPACT : Density.REGULAR);
            LOG.info("density is now {}", density);
        }

        @Override
        public Widget build(BuildContext context) {
            var bar = new Widgets.Row(
                    List.of(
                            new Widgets.Text("Goldberry", id("title")),
                            new Widgets.Spacer(),
                            new Button("Theme", widget().palette(), this::toggleTheme, false,
                                    styled("theme", "ghost"))),
                    id("bar"));

            var sidebar = new Widgets.Column(
                    List.of(
                            themes(),
                            new Widgets.Text("Clicks: " + clicks, styled("count", "caption")),
                            // Bound rather than built: this line has no state
                            // here and is never passed a value. It follows a
                            // property, and the element subscribes for as long as
                            // it is mounted.
                            Widgets.Text.of(widget().status(), styled("status", "caption")),
                            options()),
                    id("sidebar"));

            var actions = new Widgets.Row(
                    List.of(
                            new Button("Click me", widget().plus(), this::click, false,
                                    styled("click", "primary")),
                            // Disabled until there is something to undo, which is
                            // what `:disabled` is for -- and what makes it worth
                            // having in a window rather than only in a test.
                            new Button("Undo", null, this::undo, clicks == 0, id("undo")),
                            new Button("Reset", null, this::reset, clicks == 0,
                                    styled("reset", "danger"))),
                    id("actions"));

            // The prose is in the tree only when the checkbox says so, which is
            // what makes the binding worth looking at in a window: the control is
            // not decorating a boolean, it is deciding what gets built.
            var body = new java.util.ArrayList<Widget>(2);
            if (Boolean.TRUE.equals(showProse.get())) {
                body.add(new Widgets.Text(BODY_TEXT, id("prose")));
            }
            body.add(actions);
            var content = new Widgets.Column(List.copyOf(body), id("content"));

            return new Widgets.Column(
                    List.of(bar, new Widgets.Row(List.of(sidebar, content), id("body"))),
                    id("root"));
        }

        /// The composite, doing the most visible thing in the window.
        ///
        /// Two options bound to `themeName`, so picking one restyles everything —
        /// which is what makes it worth having in a window rather than only in a
        /// golden image. **Tab reaches it once**, not twice, and the arrow keys
        /// move between the options from there; Tab back into it later lands on
        /// whichever is selected, including after `Ctrl+T` has changed it from
        /// outside the group entirely ([ADR-0073]).
        ///
        /// An arrow key does not move the tick. It raises the change, `pickTheme`
        /// sets the property, and the tick follows — set `pickTheme` aside and the
        /// group stops working, exactly as the checkbox does (ADR-0063).
        /// No `class="caption"` on it, deliberately: `controls.css` sets
        /// `font-size` on `radio` itself, and a rule that matches beats a value
        /// that was inherited — so the class would look like it did something and
        /// would not. A theme moves these labels by moving `--gb-font-body`.
        private Widget themes() {
            return new RadioGroup(null, List.of(
                            new Radio("dark", "Nord dark"),
                            new Radio("light", "Nord light")),
                    themeName, this::pickTheme, false, id("themes"));
        }

        /// The three states a checkbox has, and the one route to changing one.
        ///
        /// The first is bound and wired, and clicking it really does remove the
        /// paragraph: data flows down through `showProse` and the click travels
        /// back up through `toggleProse` ([ADR-0063]).
        ///
        /// The second **starts** `MIXED` and is wired, which is the interesting
        /// one: it is bound to a property holding a tri-state [Checkbox.Value],
        /// and clicking it applies [Checkbox.Value#toggled()] — so a partial
        /// selection goes to `CHECKED` ("all of them") and never back to mixed.
        /// It used to be built with a literal `MIXED` and a null handler, which
        /// made it inert; a control that cannot move is indistinguishable from a
        /// broken one, and a showcase is the wrong place to demonstrate that.
        ///
        /// The third is disabled, so a window shows what 45% opacity looks like
        /// on a control that is not a button — and what an *actually* inert
        /// control looks like beside two that work.
        private Widget options() {
            return new Widgets.Column(
                    List.of(
                            new Checkbox("Show the prose", Checkbox.Value.UNCHECKED,
                                    showProse, this::toggleProse, false, id("prose-toggle")),
                            new Checkbox("Partly applied", Checkbox.Value.UNCHECKED,
                                    partly, this::togglePartly, false, id("partly")),
                            new Checkbox("Not available here", Checkbox.Value.CHECKED,
                                    null, null, true, id("unavailable")),
                            // The switch, bound to the same property the first
                            // checkbox reads. Two controls on one value is the
                            // point: drag the switch and the checkbox's tick
                            // moves, because neither owns the state and both are
                            // showing what `showProse` says (ADR-0063).
                            new Toggle("Show the prose (as a switch)", false,
                                    showProse, this::setProse, false, id("prose-switch")),
                            // The sixth control, and the first whose value is a
                            // number. Drag it and the readout follows the pointer
                            // 1:1 -- `bind` down, `change` up, and no arithmetic
                            // in this file at all (ADR-0079).
                            //
                            // The marks and the readout are §3's two optional
                            // halves, and they are the reason there is no longer
                            // a `text` under this line: the application used to
                            // format "Gain 40%" into a second property, and the
                            // control says it now (ADR-0080). Five marks, so the
                            // thumb lands on one at every quarter -- which is
                            // what makes a scale worth drawing rather than
                            // decoration.
                            new Slider(0, 100, 0, 5, 5, "%.0f%%", null,
                                    gain, this::setGain, false, id("gain")),
                            // Two widgets on one property, which is what the
                            // gain caption used to demonstrate: drag the slider
                            // and this follows, because neither owns the value
                            // (ADR-0063).
                            // A group of its own, because it reports rather than
                            // asks: the bar follows the same property the slider
                            // sets -- two widgets on one value, which is what the
                            // gain caption used to demonstrate (ADR-0063) -- and
                            // the spinner is the reason this window no longer
                            // goes idle. A mounted spinner asks for another frame
                            // for as long as it is there, which is what
                            // `renderer.isAnimating()` means and is exactly what
                            // something moving on screen costs (ADR-0081).
                            new Widgets.Column(
                                    List.of(
                                            new Widgets.Row(
                                                    List.of(
                                                            new Spinner(),
                                                            new Widgets.Text("Working",
                                                                    styled("busy-label", "body"))),
                                                    id("busy")),
                                            // Under the line that names it, which
                                            // is the arrangement that reads: a bar
                                            // stacked *above* a spinner is two
                                            // indicators of one thing competing,
                                            // and 4px of line under 16px of ring
                                            // has nowhere to breathe.
                                            new Progress(100, gain)),
                                    id("task"))),
                    id("options"));
        }

        /// The slider's model, and the label's.
        ///
        /// A `Property<Number>` because that is what §9's `bind` reads and what a
        /// slider is handed the read-only half of. Nothing here owns the value:
        /// the control shows what the property says, and the property changes
        /// because the handler below sets it (ADR-0063).
        private final Property<Number> gain = Property.of(40);

        /// What the slider asks for, already snapped and clamped by the control.
        ///
        /// The application does no arithmetic at all, which is the point: the
        /// range, the step, the pointer-to-value mapping **and now the readout's
        /// text** are the slider's, so an application that wanted a different
        /// range would change one number in one place (ADR-0079, ADR-0080). This
        /// method formatted "Gain 40%" into a second property until the control
        /// grew a `format`.
        void setGain(double value) {
            changed(() -> gain.set(value));
        }

        /// What the switch asks for, and the reason it is not `toggleProse`.
        ///
        /// A drag is a request for a **particular** state rather than for the
        /// other one — dragging right on a switch already on asks for on — so the
        /// value comes up with the event and this sets exactly it (ADR-0075).
        /// Flipping here instead would turn the prose off when the user dragged
        /// towards on.
        void setProse(boolean value) {
            changed(() -> showProse.set(value));
        }

        /// `setState` mutates immediately and defers the rebuild, so these read
        /// like ordinary methods and still cost one build per frame however many
        /// of them run (ADR-0052).
        void toggleTheme() {
            pickTheme("light".equals(themeName.get()) ? "dark" : "light");
        }

        /// What the radio group asks for, and what `Ctrl+T` and the bar's button
        /// go through as well.
        ///
        /// One route for three controls, which is the point of the value being a
        /// property: the group is bound to it, so a theme changed from the
        /// keyboard moves the tick without the shortcut knowing a radio group
        /// exists.
        void pickTheme(String name) {
            changed(() -> themeName.set(name));
            LOG.info("theme is now {}", theme());
        }

        /// The other half of the loop, and the reason the checkbox is controlled.
        ///
        /// This sets the property; the checkbox reads it back on the next build.
        /// Nothing in the widget tree could have done this — a widget is handed
        /// the `Observable` half, which has no `set`.
        void toggleProse() {
            changed(() -> showProse.set(!Boolean.TRUE.equals(showProse.get())));
        }

        /// The tri-state half of the same loop.
        ///
        /// [Checkbox.Value#toggled()] is what an application applies, and it is
        /// the reason the rule "clicking a partial selection asks for all of
        /// them" lives on the enum rather than in each handler: from `MIXED` this
        /// goes to `CHECKED`, and the user can never get back to mixed by
        /// clicking — only the application can put it there.
        void togglePartly() {
            changed(() -> partly.set(partly.get().toggled()));
        }

        void click() {
            changed(() -> clicks++);
        }

        void undo() {
            changed(() -> clicks = Math.max(0, clicks - 1));
        }

        void reset() {
            changed(() -> clicks = 0);
        }

        /// `setState`, and then a repaint.
        ///
        /// `setState` marks the element dirty; it does not ask for a frame,
        /// because the framework does not know whether the change is visible.
        /// An application does, and this is where it says so — once, rather than
        /// at the end of four handlers.
        private void changed(Runnable mutation) {
            setState(mutation);
            widget().onChanged().run();
        }

        /// An id, which is also the key. Keying by id is what lets the element
        /// tree match a rebuilt description to the element that already exists —
        /// so the focused button keeps its focus across a `setState` that
        /// replaced every widget in the window.
        private static Widgets.Attributes id(String id) {
            return new Widgets.Attributes(id, Set.of(), id);
        }

        private static Widgets.Attributes styled(String id, String cssClass) {
            return new Widgets.Attributes(id, Set.of(cssClass), id);
        }
    }

    // --- the application -----------------------------------------------------

    public static void main(String[] args) {
        var frameLimit = frameLimit(args);
        var painted = new int[1];

        LOG.info("Goldberry {} — showcase starting", Goldberry.version());

        var window = Window.open("Goldberry — showcase", widthOf(args), heightOf(args));

        // On the UI thread, and it has to stay there: these own native objects
        // from two libraries and are confined to the thread that built them.
        // Held for the life of the window, because building any of it per frame
        // would put font parsing and SVG parsing on the frame path.
        // A book rather than one font: the cascade resolves `font-family`,
        // `font-size` and `font-weight` per node, so a button's SemiBold label
        // and the Regular prose beside it are two faces the renderer asks for by
        // style. It opens each on first use and closes them all at the end.
        var fonts = Fonts.bundled();
        var paletteIcon = Icon.bundled("palette", ICON_SIZE);
        var plusIcon = Icon.bundled("plus", ICON_SIZE);

        var appStyles = Stylesheet.parse(CascadeLayer.APPLICATION, STYLES);

        // A theme change is a different stylesheet, so the renderer is rebuilt --
        // and only then. Everything else a rebuild changes is inside the tree.
        var renderer = new WidgetRenderer[1];
        var renderedTheme = new Theme[1];
        var renderedDensity = new Density[1];

        // The one value in this window that nothing in the widget tree owns. A
        // markup document would name it `bind="app.status"`; here it is passed in
        // directly, which is the same property either way (ADR-0062).
        var status = Property.of("checking the environment…");
        // A change marks the bound element dirty; asking for a frame is still the
        // application's job, exactly as it is for setState.
        status.subscribe(value -> window.repaint());

        var tree = new ElementTree(
                new Showcase.Screen(paletteIcon, plusIcon, status, window::repaint));
        var state = (ScreenState) tree.root().state().orElseThrow();

        // Held for the life of the window, which is the whole point of it: the
        // Yoga nodes, their layout cache and the measure callbacks behind every
        // paragraph survive from one frame to the next, and a frame where nothing
        // changed re-lays out nothing at all (ADR-0069).
        var render = RenderTree.create();

        var router = new PointerRouter();
        router.focusRoot(tree.root());
        // Ctrl+T switches the theme from the keyboard, which is what a per-window
        // accelerator map is for (§7.2, ADR-0058).
        router.shortcut("Ctrl+T", state::toggleTheme);
        // Ctrl+D switches the density, and the interesting part is what does not
        // happen: not one widget in this file mentions a height, and every
        // control still resizes (§1.3, ADR-0074).
        router.shortcut("Ctrl+D", state::toggleDensity);
        window.pointerRouter(router);

        window.onPaint(frame -> {
            // Every setState since the last frame settles here, once, however
            // many of them there were (ADR-0052).
            if (tree.needsBuild()) {
                tree.flush();
            }
            if (renderedTheme[0] != state.theme() || renderedDensity[0] != state.density()) {
                // A new renderer means new `Animations`? No -- those live on the
                // elements, which the theme swap does not touch, so a transition
                // in flight when the theme changes carries on into the new
                // colours rather than snapping (ADR-0067).
                //
                // The theme and the density are one check because they are one
                // stylesheet list: both are custom-property sheets in the same
                // cascade slot, and `Controls.stylesheets` is what knows the
                // order they go in (ADR-0074).
                var sheets = new ArrayList<>(Controls.stylesheets(state.theme(), state.density()));
                sheets.add(appStyles);
                renderer[0] = new WidgetRenderer(sheets, fonts);
                renderedTheme[0] = state.theme();
                renderedDensity[0] = state.density();
            }

            // One layout pass, two readers. `update` reconciles the retained
            // render tree against this frame's description and lays it out; the
            // paint and the hit-test snapshot both read that one result. Doing
            // this with `BoxPainter.paint` and `HitTest.capture(frame, boxes)`
            // built and laid out the Yoga tree *twice* per frame (ADR-0069).
            render.update(frame, renderer[0].render(tree));

            // What actually differs from the last frame. Computed before
            // painting, because the clip has to be in place before anything is
            // drawn (ADR-0072).
            var damage = render.damage(frame);
            if (window.canRepaintPartially()) {
                // Only the region that changed is rasterized; the rest of the
                // buffer still holds what the last frame put there. An empty list
                // means nothing changed and nothing is drawn at all.
                render.paint(frame, damage);
            } else {
                // A first frame, a resize, or a backend that promises nothing.
                render.paint(frame);
            }
            // And the backend uploads only these rectangles where the platform
            // lets it (ADR-0071).
            window.damaged(damage);

            // What the pointer is tested against is the frame that was just
            // painted -- not a fresh layout, which would be one frame ahead of
            // what the user can see (ADR-0054).
            router.updateRegions(HitTest.capture(render));

            // The whole of §1.7's "the frame loop is fully idle when no
            // animation is active": ask for another frame *only* while something
            // is moving. A window at rest costs nothing and nothing polls.
            if (renderer[0].isAnimating()) {
                window.repaint();
            }

            painted[0]++;
            if (painted[0] <= 3 || painted[0] % 50 == 0) {
                LOG.info("painted frame {} at {}", painted[0], frame.pixelSize());
            }

            if (frameLimit > 0) {
                if (painted[0] >= frameLimit) {
                    LOG.info("painted {} frame(s); exiting", painted[0]);
                    Goldberry.stop();
                } else {
                    window.repaint();
                }
            }
        });

        window.onResize(size -> LOG.info("resized to {}", size));
        window.onScaleChange(scale -> LOG.info("scale is now {}", scale));
        window.onCloseRequest(() -> {
            LOG.info("close requested");
            return true;
        });

        // Work that is not instant belongs off the UI thread. It comes back on
        // it automatically, so touching the window here is safe (ADR-0020).
        Goldberry.async(Showcase::describeEnvironment)
                .thenAccept(text -> {
                    window.title("Goldberry — " + text);
                    // Nothing here reaches into the tree: the property is set, and
                    // the sidebar line bound to it redraws itself.
                    status.set(text);
                });

        try {
            Goldberry.run();
        } finally {
            // After the loop, not in a try-with-resources around it: the paint
            // callback holds the font and the icons, and runs until `run`
            // returns.
            tree.unmount();
            // Before the fonts: a render object holds a Yoga measure callback
            // that closes over a paragraph, and a paragraph over a font.
            render.close();
            plusIcon.close();
            paletteIcon.close();
            // Every font, then every face it shares -- which is the ordering the
            // book does for us: closing a face while a font still holds it
            // leaves Blend2D reading unmapped memory.
            fonts.close();

            // And then hand the window back before the process goes away.
            //
            // This is not tidiness. `Goldberry.stop()` ends the loop with the
            // window still open, so without this the process exits with a live
            // Wayland surface and SDL never quit: no xdg_toplevel.destroy, no
            // wl_surface.destroy, no SDL_Quit. The compositor finds out its
            // client is gone when the socket closes, and has to unwind a
            // connection that never said goodbye.
            //
            // A compositor must survive that -- every killed process does it --
            // and GNOME 46's Mutter does not always: it crashed in
            // wl_client_destroy -> its destroy listener -> g_signal_handler_disconnect
            // while cleaning up after exactly this exit. Disconnecting properly
            // is right regardless of whose bug that is.
            Goldberry.shutdown();
        }
        LOG.info("showcase finished");
    }

    /// Stands in for real background work — reading a config file, loading a
    /// font, talking to a service. The point is where its result lands.
    private static String describeEnvironment() {
        LOG.debug("describing the environment on {}", Thread.currentThread());
        return "showcase on " + System.getProperty("os.name")
                + " / " + System.getProperty("os.arch");
    }

    /// `--frames=N` paints N frames and exits, so CI can prove a window opened
    /// without a human to close it. Absent, the window stays until closed.
    private static int frameLimit(String[] args) {
        return intArgument(args, "--frames=", 0);
    }

    /// `--size=WxH` opens at a size other than the default — for looking at what
    /// a frame costs when there are two million pixels of it.
    private static float widthOf(String[] args) {
        return sizeArgument(args, 0, 960);
    }

    private static float heightOf(String[] args) {
        return sizeArgument(args, 1, 640);
    }

    private static float sizeArgument(String[] args, int index, float fallback) {
        for (var arg : args) {
            if (arg.startsWith("--size=")) {
                var parts = arg.substring("--size=".length()).split("x");
                if (parts.length == 2) {
                    return Float.parseFloat(parts[index]);
                }
            }
        }
        return fallback;
    }

    private static int intArgument(String[] args, String prefix, int fallback) {
        for (var arg : args) {
            if (arg.startsWith(prefix)) {
                return Integer.parseInt(arg.substring(prefix.length()));
            }
        }
        return fallback;
    }
}
