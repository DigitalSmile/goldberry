package io.github.digitalsmile.goldberry.media.audio;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;

/// [OutputLatency#firstOf]: providers asked in turn, the first usable answer
/// taken.
///
/// A negative answer is not usable (no device hears a sample before it is
/// handed over), and neither is an exception: both are passed over, the second
/// logged once per provider so a broken one is seen without flooding a log the
/// audio thread writes to every second.
///
/// @param providers the providers, in the order asked
record FirstAnswer(List<OutputLatency> providers) implements OutputLatency {

    private static final Logger LOG = Logs.of(FirstAnswer.class);

    FirstAnswer {
        providers = List.copyOf(providers);
    }

    @Override
    public Optional<Duration> defaultOutput() {
        for (var provider : providers) {
            Optional<Duration> answer;
            try {
                answer = provider.defaultOutput();
            } catch (RuntimeException e) {
                LOG.debug("the output latency provider {} failed, and is passed over", provider, e);
                continue;
            }
            if (answer != null && answer.isPresent() && !answer.get().isNegative()) {
                return answer;
            }
        }
        return Optional.empty();
    }
}
