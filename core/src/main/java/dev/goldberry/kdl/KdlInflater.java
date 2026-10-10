package dev.goldberry.kdl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// Turns markup into objects, through a registry of node name to factory.
///
/// A runtime registry of node name to factory. Built-ins and application widgets
/// register identically; an unknown node is a hard error with a source position.
///
/// Generic in what it builds. The inflater knows nothing about widgets: a factory
/// takes a [KdlNode] and its already-inflated children and returns whatever the
/// application is building. The widget tree inflates to widgets and a test can
/// inflate to anything else; neither requires this class to change.
///
/// ## Wiring
///
/// `id` lookup is here. Binding `action` names to a controller is **not** — that
/// happens through an explicit `bind(controller)` call and never through
/// reflective handler lookup, and it belongs with the widget tree that has
/// actions to bind.
///
/// ## Properties nothing read
///
/// A property no factory asks for has no effect, so a misspelt one is a widget
/// that silently ignores what its author wrote. The inflater notes every
/// property asked for while a document is built and, once the whole tree is
/// built, does what its [UnreadPolicy] says with the rest: by default it refuses
/// the document, as it refuses an unknown node.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#parsing-and-inflating).
///
/// @param <T> what nodes inflate into
public final class KdlInflater<T> {

    /// Builds one node.
    ///
    /// @param <T> the built type
    @FunctionalInterface
    public interface Factory<T> {

        /// @param node     the markup node, for its arguments and properties
        /// @param children this node's children, already inflated
        T create(KdlNode node, List<T> children);
    }

    private static final Logger LOG = Logs.of(KdlInflater.class);

    private final Map<String, Factory<T>> factories = new LinkedHashMap<>();
    private UnreadPolicy unread = UnreadPolicy.REFUSE;

    /// An inflater that knows nothing yet. Built-ins and application widgets
    /// both arrive through [#register].
    public KdlInflater() {}

    /// Registers a factory for `name`.
    ///
    /// Registering the same name twice is refused. Built-ins and application
    /// widgets register identically, which means an application
    /// *can* shadow a built-in — but silently, at whichever point its
    /// registration happened to run, is not a good way to find that out.
    ///
    /// @throws IllegalStateException if `name` is already registered
    public KdlInflater<T> register(String name, Factory<T> factory) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(factory, "factory");
        if (factories.putIfAbsent(name, factory) != null) {
            throw new IllegalStateException(
                    "\"" + name + "\" is already registered; use replace() to shadow it deliberately");
        }
        return this;
    }

    /// Registers a factory, replacing any existing one.
    ///
    /// The deliberate version of shadowing a built-in.
    public KdlInflater<T> replace(String name, Factory<T> factory) {
        factories.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(factory, "factory"));
        return this;
    }

    /// Sets what happens to a property nothing read; [UnreadPolicy#REFUSE] until
    /// this is called.
    public KdlInflater<T> unread(UnreadPolicy policy) {
        unread = Objects.requireNonNull(policy, "policy");
        return this;
    }

    /// What happens to a property nothing read.
    public UnreadPolicy unread() {
        return unread;
    }

    /// The names this inflater knows, in registration order.
    public List<String> registered() {
        return List.copyOf(factories.keySet());
    }

    /// Inflates every node in a document.
    ///
    /// @throws KdlSyntaxException if any node names something unregistered, or
    ///         under [UnreadPolicy#REFUSE] carries a property nothing read
    public List<T> inflateAll(List<KdlNode> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        return checked(nodes, () -> {
            var built = new ArrayList<T>(nodes.size());
            for (var node : nodes) {
                built.add(build(node));
            }
            return List.copyOf(built);
        });
    }

    /// Inflates one node and its subtree.
    ///
    /// Depth first, so a factory is handed children that are already built and
    /// never has to inflate anything itself.
    ///
    /// @throws KdlSyntaxException if this node or any below it is unregistered,
    ///         or under [UnreadPolicy#REFUSE] carries a property nothing read
    public T inflate(KdlNode node) {
        Objects.requireNonNull(node, "node");
        return checked(List.of(node), () -> build(node));
    }

    private T build(KdlNode node) {
        var factory = factories.get(node.name());
        if (factory == null) {
            throw new KdlSyntaxException(
                    "unknown node \"" + node.name() + "\"; registered: " + String.join(", ", registered()),
                    node.line(),
                    node.column());
        }
        var children = new ArrayList<T>(node.children().size());
        for (var child : node.children()) {
            children.add(build(child));
        }
        return factory.create(node, List.copyOf(children));
    }

    /// Builds `nodes` with reads being noted, then deals with what was not read.
    ///
    /// The check waits for the whole tree because a factory is not the only
    /// reader of its node: a parent may read its children's properties after
    /// their own factories have run.
    private <R> R checked(List<KdlNode> nodes, Supplier<R> building) {
        if (unread == UnreadPolicy.IGNORE) {
            return building.get();
        }
        var reads = new PropertyReads();
        var built = ScopedValue.where(PropertyReads.CURRENT, reads).call(building::get);
        var ignored = reads.unread(nodes);
        if (ignored.isEmpty()) {
            return built;
        }
        switch (unread) {
            case REFUSE -> throw refusal(ignored);
            case WARN -> ignored.forEach(property -> LOG.warn("{}", property.describe()));
            case IGNORE -> throw new AssertionError("an ignoring inflater notes nothing");
        }
        return built;
    }

    private static KdlSyntaxException refusal(List<UnreadProperty> ignored) {
        var first = ignored.getFirst();
        var message = new StringBuilder(first.describe());
        if (ignored.size() > 1) {
            message.append("; also ");
            message.append(String.join(
                    "; ",
                    ignored.subList(1, ignored.size()).stream()
                            .map(UnreadProperty::describe)
                            .toList()));
        }
        return new KdlSyntaxException(message.toString(), first.line(), first.column());
    }

    /// Finds a node by its `id` property, anywhere in the document.
    ///
    /// The lookup half of the wiring, and it works on the *markup* rather than
    /// on what was built: an id identifies a node in the document, and what that
    /// node became is the caller's business.
    ///
    /// @throws KdlSyntaxException if two nodes share an id, which is a mistake
    ///         that otherwise shows up as a handler firing on the wrong widget
    public static Optional<KdlNode> byId(List<KdlNode> nodes, String id) {
        Objects.requireNonNull(id, "id");
        var found = new ArrayList<KdlNode>();
        collectById(nodes, id, found);
        if (found.size() > 1) {
            var second = found.get(1);
            throw new KdlSyntaxException(
                    "id \"" + id + "\" is used more than once; also at "
                            + found.getFirst().position(),
                    second.line(),
                    second.column());
        }
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    private static void collectById(List<KdlNode> nodes, String id, List<KdlNode> found) {
        for (var node : nodes) {
            if (id.equals(node.stringProperty("id"))) {
                found.add(node);
            }
            collectById(node.children(), id, found);
        }
    }
}
