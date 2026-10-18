package dev.goldberry.platform;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// The capabilities as a set whose probed members are asked about only when
/// somebody asks about them.
///
/// Most of what [PlatformCapabilities] reports is a bit compiled into
/// `libgoldberry`, read in one call. A few members are a second library being
/// there instead, and answering one means opening it: [Capability#WEB_VIEW] is
/// `dlopen` of `libgoldberry-webview`, and on Linux that pulls in WebKitGTK,
/// about 26 ms that every start paid for a question most never ask. So
/// [#contains] answers a probed member from its own probe, run once, and the
/// rest from the bits. The bits are also read once, when they are first needed.
///
/// Iterating, sizing or printing the set answers every member, so whatever it
/// says is the whole truth. Only [#contains] is narrow.
final class LazyCapabilities extends AbstractSet<Capability> {

    private final Once<Set<Capability>> bits;
    private final Map<Capability, Once<Boolean>> probes;

    /// @param bits   the members that are not probed, read on first need
    /// @param probes one question per probed member, each asked at most once
    LazyCapabilities(Supplier<Set<Capability>> bits, Map<Capability, BooleanSupplier> probes) {
        this.bits = new Once<>(Objects.requireNonNull(bits, "bits"));
        var once = new EnumMap<Capability, Once<Boolean>>(Capability.class);
        probes.forEach((capability, probe) -> once.put(capability, new Once<>(probe::getAsBoolean)));
        this.probes = Collections.unmodifiableMap(once);
    }

    @Override
    public boolean contains(Object o) {
        if (!(o instanceof Capability capability)) {
            return false;
        }
        var probe = probes.get(capability);
        return probe != null ? probe.get() : bits.get().contains(capability);
    }

    @Override
    public Iterator<Capability> iterator() {
        return all().iterator();
    }

    @Override
    public int size() {
        return all().size();
    }

    /// Every member, which asks every probe. Declaration order, as an `EnumSet`
    /// keeps it, and unmodifiable, so the iterator cannot remove.
    private Set<Capability> all() {
        var all = EnumSet.noneOf(Capability.class);
        for (var capability : bits.get()) {
            if (!probes.containsKey(capability)) {
                all.add(capability);
            }
        }
        probes.forEach((capability, probe) -> {
            if (probe.get()) {
                all.add(capability);
            }
        });
        return Collections.unmodifiableSet(all);
    }

    /// A value computed on first request and kept. Thread-safe: two threads
    /// asking together compute it once.
    private static final class Once<T> {
        private final Supplier<T> supplier;
        private volatile @Nullable T value;

        Once(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        T get() {
            var known = value;
            if (known == null) {
                synchronized (this) {
                    known = value;
                    if (known == null) {
                        known = Objects.requireNonNull(supplier.get(), "a capability answer");
                        value = known;
                    }
                }
            }
            return known;
        }
    }
}
