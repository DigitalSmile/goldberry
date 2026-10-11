package dev.goldberry.motion;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Corners;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.KeyframeAnimations;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.cascade.Transitions.Animatable;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.IndividualTransform;
import dev.goldberry.css.value.Transform;
import dev.goldberry.css.value.TransformBox;

/// What moves now that did not: the corners and the focus ring, the
/// `translate`, `rotate` and `scale` properties, a keyframe's own curve and a
/// paused animation.
class MotionDetailsTest {

    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    private static ComputedStyle style(String declarations) {
        return style(new StyleResolver(
                List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "panel { " + declarations + " }"))));
    }

    private static ComputedStyle style(StyleResolver resolver) {
        return ComputedStyle.of(resolver.resolve(element("panel")), CONTEXT);
    }

    @Nested
    @DisplayName("paint-only properties")
    class PaintOnly {

        @Test
        @DisplayName("border-radius moves corner by corner, percentages and all")
        void radius() {
            var from = style("border-radius: 0");
            var to = style("border-radius: 10px 50%");
            var half = Animatables.interpolate(
                    Animatable.BORDER_RADIUS,
                    Animatables.valueOf(from, Animatable.BORDER_RADIUS),
                    Animatables.valueOf(to, Animatable.BORDER_RADIUS),
                    0.5);

            var corners = (Corners) half;
            assertEquals(5, corners.topLeftRadius().x(), 1e-9);
            assertEquals(25, corners.topRightRadius().xPercent(), 1e-9);
        }

        @Test
        @DisplayName("the ring's colour and offset move")
        void outline() {
            var from = style("outline: 2px solid #000000; outline-offset: 0");
            var to = style("outline: 2px solid #ffffff; outline-offset: 4px");

            var colour = Animatables.interpolate(
                    Animatable.OUTLINE_COLOR,
                    Animatables.valueOf(from, Animatable.OUTLINE_COLOR),
                    Animatables.valueOf(to, Animatable.OUTLINE_COLOR),
                    0.5);
            var offset = Animatables.interpolate(
                    Animatable.OUTLINE_OFFSET,
                    Animatables.valueOf(from, Animatable.OUTLINE_OFFSET),
                    Animatables.valueOf(to, Animatable.OUTLINE_OFFSET),
                    0.5);
            var moved = Animatables.withValue(
                    Animatables.withValue(from, Animatable.OUTLINE_COLOR, colour), Animatable.OUTLINE_OFFSET, offset);

            assertEquals(2, moved.decoration().outlineOffset(), 1e-9);
            var grey = moved.decoration().outlineColor() & 0xFF;
            assertTrue(
                    grey > 0x40 && grey < 0xC0,
                    Integer.toHexString(moved.decoration().outlineColor()));
        }

        @Test
        @DisplayName("each is a transition's property by its CSS name")
        void transitionable() {
            var transitions = style("transition: border-radius 200ms, outline-color 100ms, scale 50ms")
                    .transitions();
            assertNotNull(transitions.get(Animatable.BORDER_RADIUS));
            assertNotNull(transitions.get(Animatable.OUTLINE_COLOR));
            assertNotNull(transitions.get(Animatable.SCALE));
            assertNull(transitions.get(Animatable.OUTLINE_OFFSET));
        }
    }

    @Nested
    @DisplayName("translate, rotate and scale")
    class Individual {

        @Test
        @DisplayName("apply before transform: translate, then rotate, then scale")
        void order() {
            var transform = style("translate: 10px 0; rotate: 90deg; scale: 2; transform: translate(1px, 0);"
                            + " transform-origin: 0 0")
                    .transform();
            var matrix = transform.matrix(100, 100);

            // A point moved by transform's 1px, scaled by 2, turned a quarter
            // clockwise, then moved 10px right: (x, y) lands on (10 - 2y, 2x + 2).
            assertEquals(10, matrix.mapX(0, 0), 1e-9);
            assertEquals(2, matrix.mapY(0, 0), 1e-9);
            assertEquals(10, matrix.mapX(1, 0), 1e-9);
            assertEquals(4, matrix.mapY(1, 0), 1e-9);
        }

        @Test
        @DisplayName("are set and animated apart from transform and from each other")
        void apart() {
            var both = style("transform: rotate(10deg); scale: 1.5").transform();
            assertEquals(1, both.functions().size());
            assertNotNull(both.individual().scale());
            assertNull(both.individual().rotate());

            var from = Animatables.valueOf(style("translate: 0"), Animatable.TRANSLATE);
            var to = Animatables.valueOf(style("translate: 10px 20px"), Animatable.TRANSLATE);
            var half = (IndividualTransform) Animatables.interpolate(Animatable.TRANSLATE, from, to, 0.5);
            assertNotNull(half.translate());
            assertEquals(5, half.translate().x().resolve(0), 1e-9);
            assertEquals(10, half.translate().y().resolve(0), 1e-9);

            var none = Animatables.valueOf(style("color: red"), Animatable.SCALE);
            var grown = Animatables.valueOf(style("scale: 2"), Animatable.SCALE);
            var between = (IndividualTransform) Animatables.interpolate(Animatable.SCALE, none, grown, 0.5);
            assertEquals(1.5, between.scale().x(), 1e-9, "none grows from the identity");
        }

        @Test
        @DisplayName("a transform transition leaves them where they are")
        void transformLeavesThem() {
            var styled = style("scale: 2; transform: rotate(10deg)");
            var moved = Animatables.withValue(styled, Animatable.TRANSFORM, Transform.NONE);

            assertTrue(moved.transform().functions().isEmpty());
            assertNotNull(moved.transform().individual().scale());
        }

        @Test
        @DisplayName("rotate reads `z` and nothing else of an axis, and none is none")
        void parsing() {
            assertNotNull(style("rotate: z 45deg").transform().individual().rotate());
            assertNull(style("rotate: x 45deg").transform().individual().rotate());
            assertNull(style("scale: 2; scale: none").transform().individual().scale());
            assertTrue(style("translate: none").transform().isNone());
        }

        @Test
        @DisplayName("`transform-box: content-box` measures the origin inside the padding")
        void contentBox() {
            var transform = style("transform-box: fill-box; transform-origin: 0 0; rotate: 180deg")
                    .transform();
            assertEquals(TransformBox.CONTENT_BOX, transform.box());

            var matrix = transform.matrix(100, 100, 10, 10, 10, 10);
            // Half a turn about (10, 10): the corner (0, 0) lands at (20, 20).
            assertEquals(20, matrix.mapX(0, 0), 1e-9);
            assertEquals(20, matrix.mapY(0, 0), 1e-9);
        }
    }

    @Nested
    @DisplayName("keyframes")
    class Keyframes {

        private static final String CSS = """
                panel { animation: fade 1000ms linear }
                @keyframes fade {
                  from { opacity: 0; animation-timing-function: ease-in }
                  to { opacity: 1 }
                }
                """;

        private final StyleResolver resolver =
                new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, CSS)));

        private final TestElement panel = element("panel");

        private KeyframeTrack track(ComputedStyle target) {
            return KeyframeTrack.resolve(
                    resolver.keyframes("fade"), target, frame -> resolver.resolveKeyframe(panel, frame), CONTEXT);
        }

        @Test
        @DisplayName("a keyframe's own curve runs from it to the next")
        void ownCurve() {
            var target = style(resolver);
            var half = track(target).apply(target, 0.5, Easing.LINEAR);

            assertTrue(half.opacity() < 0.45, "ease-in is behind linear half way: " + half.opacity());
        }

        @Test
        @DisplayName("a paused animation holds where its clock stopped and goes on from there")
        void paused() {
            var running = style(resolver);
            var paused =
                    running.animations(running.animations().playStates(List.of(KeyframeAnimations.PlayState.PAUSED)));
            var animations = new Animations();
            Animations.KeyframeSource source = (name, target) -> track(target);

            animations.animate(running, running, 0, source);
            var at400 = animations.animate(paused, paused, 400, source).opacity();
            var held = animations.animate(paused, paused, 900, source).opacity();
            assertEquals(at400, held, 1e-9, "nothing moves while it is paused");
            assertFalse(animations.isAnimating(), "and it asks for no frames");

            var resumed = animations.animate(running, running, 1000, source).opacity();
            assertEquals(at400, resumed, 1e-9, "it goes on from 400ms, not from 1000ms");
            assertTrue(animations.animate(running, running, 1100, source).opacity() > resumed);
        }
    }
}
