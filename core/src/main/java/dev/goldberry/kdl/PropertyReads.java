package dev.goldberry.kdl;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// Which properties of which nodes were asked for while a document was inflated.
///
/// Bound for the length of one outermost [KdlInflater#inflate] or
/// [KdlInflater#inflateAll], and written to by [KdlNode]'s accessors. A node is
/// keyed by identity rather than by value: two `button "OK"` nodes in one
/// document are equal records and are still two places an author wrote.
///
/// Kept until the whole tree is built rather than per factory, because a
/// factory is not the only reader of its node: `line-chart` reads its `point`
/// children's `x` and `y` after their own factories have run.
final class PropertyReads {

    /// The reads of the inflation running on this thread, if one is.
    static final ScopedValue<PropertyReads> CURRENT = ScopedValue.newInstance();

    private final IdentityHashMap<KdlNode, Set<String>> read = new IdentityHashMap<>();

    /// Notes that `key` was asked of `node`, when an inflation is listening.
    static void note(KdlNode node, String key) {
        if (CURRENT.isBound()) {
            CURRENT.get().read.computeIfAbsent(node, _ -> new LinkedHashSet<>()).add(key);
        }
    }

    /// Notes that every property of `node` was asked for, as a caller walking
    /// the whole map does.
    static void noteAll(KdlNode node, Map<String, KdlValue> properties) {
        if (CURRENT.isBound()) {
            CURRENT.get().read.computeIfAbsent(node, _ -> new LinkedHashSet<>()).addAll(properties.keySet());
        }
    }

    /// `properties` as `node` hands them out: the map itself when nothing is
    /// listening, and a view that notes what it is asked when something is.
    static Map<String, KdlValue> view(KdlNode node, Map<String, KdlValue> properties) {
        return CURRENT.isBound() ? new Noting(node, properties) : properties;
    }

    /// Every property in `nodes` and their subtrees that nothing asked for, in
    /// document order.
    List<UnreadProperty> unread(List<KdlNode> nodes) {
        var found = new ArrayList<UnreadProperty>();
        collect(nodes, found);
        return List.copyOf(found);
    }

    private void collect(List<KdlNode> nodes, List<UnreadProperty> found) {
        for (var node : nodes) {
            var asked = read.getOrDefault(node, Set.of());
            node.propertiesUnnoted().forEach((key, value) -> {
                if (!asked.contains(key)) {
                    found.add(new UnreadProperty(
                            node.name(), key, value, List.copyOf(asked), node.line(), node.column()));
                }
            });
            collect(node.children(), found);
        }
    }

    /// A node's properties that note a lookup by key, and note all of them
    /// when anything walks the map.
    private static final class Noting extends AbstractMap<String, KdlValue> {

        private final KdlNode node;
        private final Map<String, KdlValue> properties;

        Noting(KdlNode node, Map<String, KdlValue> properties) {
            this.node = node;
            this.properties = properties;
        }

        @Override
        public @Nullable KdlValue get(Object key) {
            if (key instanceof String name) {
                note(node, name);
            }
            return properties.get(key);
        }

        @Override
        public boolean containsKey(Object key) {
            if (key instanceof String name) {
                note(node, name);
            }
            return properties.containsKey(key);
        }

        @Override
        public Set<Entry<String, KdlValue>> entrySet() {
            noteAll(node, properties);
            return properties.entrySet();
        }

        @Override
        public int size() {
            return properties.size();
        }
    }
}
