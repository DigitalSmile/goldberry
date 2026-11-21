package dev.goldberry.mic.view;

import java.util.concurrent.atomic.AtomicInteger;

import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.SampleListener;
import dev.goldberry.mic.source.SampleSource;

/// A source that counts the listeners it holds, over one that delivers.
final class CountingSource implements SampleSource {

    private final SampleSource delegate;
    final AtomicInteger listening = new AtomicInteger();

    CountingSource(SampleSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public SampleFormat format() {
        return delegate.format();
    }

    @Override
    public AutoCloseable onSamples(SampleListener listener) {
        var subscription = delegate.onSamples(listener);
        listening.incrementAndGet();
        return () -> {
            subscription.close();
            listening.decrementAndGet();
        };
    }
}
