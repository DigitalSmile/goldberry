package dev.goldberry.vector;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.golden.GoldenImage;
import dev.goldberry.widgets.core.image.Fit;

/// What ThorVG draws of each kind of thing a document holds, against goldens:
/// at the golden's size and, through the harness's sweep, at twice and one and
/// a half times it, which is the same document asked for a larger raster.
@DisplayName("Svg, against goldens")
class SvgGoldenTest {

    /// Paths, strokes with joins and dashes, both gradients, a `<use>` of a
    /// symbol, a clip, a mask and group opacity, in a 96 by 48 document.
    static final String FEATURES = """
            <svg xmlns="http://www.w3.org/2000/svg" width="96" height="48" viewBox="0 0 96 48">
              <defs>
                <linearGradient id="sky" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0" stop-color="#5e81ac"/>
                  <stop offset="1" stop-color="#88c0d0"/>
                </linearGradient>
                <radialGradient id="sun" cx="0.5" cy="0.5" r="0.5">
                  <stop offset="0" stop-color="#ebcb8b"/>
                  <stop offset="1" stop-color="#d08770" stop-opacity="0"/>
                </radialGradient>
                <clipPath id="porthole"><circle cx="72" cy="24" r="14"/></clipPath>
                <mask id="fade">
                  <rect x="0" y="0" width="96" height="48" fill="white"/>
                  <rect x="0" y="36" width="96" height="12" fill="#404040"/>
                </mask>
                <symbol id="star" viewBox="0 0 10 10">
                  <path d="M5 0 L6.2 3.6 L10 3.6 L7 5.9 L8.1 9.5 L5 7.3 L1.9 9.5 L3 5.9 L0 3.6 L3.8 3.6 Z"
                        fill="#eceff4"/>
                </symbol>
              </defs>
              <g mask="url(#fade)">
                <rect x="0" y="0" width="96" height="48" fill="url(#sky)"/>
                <circle cx="20" cy="18" r="14" fill="url(#sun)"/>
                <path d="M0 40 Q24 24 48 40 T96 40 L96 48 L0 48 Z" fill="#a3be8c"/>
              </g>
              <path d="M8 8 L30 30 L52 8" fill="none" stroke="#bf616a" stroke-width="4"
                    stroke-linejoin="round" stroke-linecap="round"/>
              <line x1="4" y1="44" x2="92" y2="44" stroke="#2e3440" stroke-width="2" stroke-dasharray="6 3"/>
              <g clip-path="url(#porthole)">
                <rect x="56" y="8" width="32" height="32" fill="#b48ead"/>
                <rect x="56" y="24" width="32" height="16" fill="#4c566a"/>
              </g>
              <use href="#star" x="64" y="2" width="8" height="8"/>
              <use href="#star" x="80" y="2" width="12" height="12" opacity="0.5"/>
            </svg>
            """;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Test
    @DisplayName("every feature, at the document's own size")
    void features() {
        try (var svg = Svg.parse(FEATURES)) {
            GoldenImage.assertMatches("svg-features", 96, 48, 1f, (size, scale) -> svg.render(size));
        }
    }

    @Test
    @DisplayName("every feature, contained in a square")
    void contained() {
        try (var svg = Svg.parse(FEATURES)) {
            GoldenImage.assertMatches(
                    "svg-features-contained", 64, 64, 1f, (size, scale) -> svg.render(size, Fit.CONTAIN));
        }
    }
}
