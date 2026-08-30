package io.github.digitalsmile.goldberry.bench;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.select.Selector;

/// The cascade, measured properly.
///
/// `docs/testing.md` §1.5 asks for JMH on the hot seams, and this is the first:
/// resolving a style is on the frame path, it runs once per element per restyle,
/// and it is the one part of that path made of pure logic — no native call, no
/// font, no window. That makes it the seam where a microbenchmark says something
/// a wall-clock frame measurement cannot.
///
/// **This does not replace the `benchmark` task.** That one measures whole
/// operations against a real rasterizer and prints numbers to argue about
/// (ADR-0028, ADR-0031); a threshold on shared CI hardware fails for reasons
/// that have nothing to do with the code, and neither of these asserts anything.
/// What JMH adds is the discipline: forks, warm-up, and a blackhole, so the
/// number is not an artifact of the JIT having specialised the loop away.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CascadeBenchmark {

    private StyleResolver resolver;
    private Probe element;

    /// A element with a type, an id and two classes — the shape a real widget
    /// presents to the cascade, and the one that exercises specificity rather
    /// than short-circuiting on the first miss.
    private record Probe(String type, String id, java.util.Set<String> classes)
            implements io.github.digitalsmile.goldberry.css.StyleElement {

        @Override
        public boolean hasState(Selector.PseudoClass state) {
            return false;
        }

        /// No ancestor: this benchmark measures resolving one element against a
        /// sheet, not walking a tree. A parent would put descendant-combinator
        /// matching in the number and make it two measurements in a trench coat.
        @Override
        public io.github.digitalsmile.goldberry.css.StyleElement parent() {
            return null;
        }
    }

    @Setup
    public void setUp() {
        resolver = new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, """
                button { color: #eceff4; background: #4c566a; padding: 6px 12px }
                button.primary { background: #5e81ac }
                button.primary:hover { background: #81a1c1 }
                button#save { font-weight: 600 }
                text { color: #d8dee9 }
                panel > text { color: #e5e9f0 }
                """)));
        element = new Probe("button", "save", java.util.Set.of("primary", "wide"));
    }

    @Benchmark
    public void resolveOneElement(Blackhole hole) {
        hole.consume(resolver.resolve(element));
    }
}
