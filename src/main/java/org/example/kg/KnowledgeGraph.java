package org.example.kg;

import org.example.Type;
import org.example.ner.Entity;

import java.util.*;

/**
 * The knowledge graph itself: gene and disease nodes joined by relation edges.
 * Mentions are merged by normalized surface form, so every paper that says CEACAM1 contributes to the same node and
 * the edge weight becomes the number of sentences in the corpus that support the statement. Each edge keeps its
 * supporting triples, which is what makes an edge clickable back to the sentence it came from.
 */
public final class KnowledgeGraph {

    private final LinkedHashMap<String, Node> nodes;
    private final LinkedHashMap<String, Edge> edges;

    public KnowledgeGraph() {
        this.nodes = new LinkedHashMap<>();
        this.edges = new LinkedHashMap<>();
    }

    /**
     * Adds a triple, creating its nodes and edge when they are seen for the first time.
     * @param triple Triple to add.
     */
    public void add(Triple triple) {
        Node subject = node(triple.getSubject());
        Node object = node(triple.getObject());
        edges.computeIfAbsent(subject.getId() + "\t" + triple.getPredicate() + "\t" + object.getId(), key -> new Edge(subject, object, triple.getPredicate())).record(triple);
    }

    /**
     * Adds every triple of a collection.
     * @param triples Triples to add.
     */
    public void addAll(Collection<Triple> triples) {
        for (Triple triple : triples) {
            add(triple);
        }
    }

    private Node node(Entity entity) {
        Node node = nodes.computeIfAbsent(entity.nodeId(), id -> new Node(id, entity.getType(), entity.getText()));
        node.record(entity.getText());
        return node;
    }

    public Collection<Node> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Collection<Edge> getEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    public Node getNode(String id) {
        return nodes.get(id);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    /**
     * Returns the nodes of one type.
     * @param type Type to select.
     * @return The matching nodes.
     */
    public List<Node> getNodes(Type type) {
        ArrayList<Node> selected = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (node.getType() == type) {
                selected.add(node);
            }
        }
        return selected;
    }

    /**
     * Returns the edges touching a node, in either direction.
     * @param node Node to look up.
     * @return The incident edges.
     */
    public List<Edge> edgesOf(Node node) {
        ArrayList<Edge> touching = new ArrayList<>();
        for (Edge edge : edges.values()) {
            if (edge.getSource() == node || edge.getTarget() == node) {
                touching.add(edge);
            }
        }
        return touching;
    }

    /**
     * Returns the nodes adjacent to a node.
     * @param node Node to look up.
     * @return The neighbours.
     */
    public Set<Node> neighbours(Node node) {
        LinkedHashSet<Node> result = new LinkedHashSet<>();
        for (Edge edge : edgesOf(node)) {
            result.add(edge.getSource() == node ? edge.getTarget() : edge.getSource());
        }
        return result;
    }

    /**
     * Checks whether two nodes are already connected by any predicate.
     * @param a First node.
     * @param b Second node.
     * @return True when an edge joins them.
     */
    public boolean isConnected(Node a, Node b) {
        for (Edge edge : edges.values()) {
            if ((edge.getSource() == a && edge.getTarget() == b) || (edge.getSource() == b && edge.getTarget() == a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies the graph, keeping only the edges with enough supporting sentences. Single-sentence edges are mostly
     * extraction noise, so raising the threshold is the quickest way to a readable picture.
     * @param minimumSupport Least number of supporting sentences an edge needs.
     * @return The filtered graph.
     */
    public KnowledgeGraph filter(int minimumSupport) {
        KnowledgeGraph filtered = new KnowledgeGraph();
        for (Edge edge : edges.values()) {
            if (edge.getSupport() >= minimumSupport) {
                filtered.addAll(edge.getEvidence());
            }
        }
        return filtered;
    }

    /**
     * Returns the best connected nodes.
     * @param limit Number of nodes to return.
     * @return The nodes ordered by how many edges they take part in.
     */
    public List<Node> hubs(int limit) {
        LinkedHashMap<Node, Integer> degrees = new LinkedHashMap<>();
        for (Edge edge : edges.values()) {
            degrees.merge(edge.getSource(), 1, Integer::sum);
            degrees.merge(edge.getTarget(), 1, Integer::sum);
        }
        ArrayList<Node> ranked = new ArrayList<>(degrees.keySet());
        ranked.sort((a, b) -> Integer.compare(degrees.get(b), degrees.get(a)));
        return ranked.size() > limit ? new ArrayList<>(ranked.subList(0, limit)) : ranked;
    }

    /**
     * Counts the edges of a node.
     * @param node Node to measure.
     * @return The degree of the node.
     */
    public int degree(Node node) {
        int degree = 0;
        for (Edge edge : edges.values()) {
            if (edge.getSource() == node || edge.getTarget() == node) {
                degree++;
            }
        }
        return degree;
    }

    @Override
    public String toString() {
        return nodeCount() + " nodes (" + getNodes(Type.GENE).size() + " genes, " + getNodes(Type.DISEASE).size() + " diseases), " + edgeCount() + " edges";
    }

    /**
     * A gene or a disease, merged across every mention of it.
     */
    public static final class Node {

        private final String id;
        private final Type type;
        private final LinkedHashMap<String, Integer> surfaceForms;
        private String label;
        private int mentions;

        Node(String id, Type type, String label) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.surfaceForms = new LinkedHashMap<>();
        }

        void record(String surfaceForm) {
            mentions++;
            int count = surfaceForms.merge(surfaceForm, 1, Integer::sum);
            if (count > surfaceForms.getOrDefault(label, 0)) {
                label = surfaceForm;
            }
        }

        public String getId() {
            return id;
        }

        public Type getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

        public int getMentions() {
            return mentions;
        }

        @Override
        public String toString() {
            return label + " (" + type + ", " + mentions + " mentions)";
        }
    }

    /**
     * A relation between two nodes, weighted by how many sentences state it.
     */
    public static final class Edge {

        private final Node source;
        private final Node target;
        private final String predicate;
        private final ArrayList<Triple> evidence;
        private final LinkedHashSet<String> documents;

        Edge(Node source, Node target, String predicate) {
            this.source = source;
            this.target = target;
            this.predicate = predicate;
            this.evidence = new ArrayList<>();
            this.documents = new LinkedHashSet<>();
        }

        void record(Triple triple) {
            evidence.add(triple);
            documents.add(triple.getDocument());
        }

        public Node getSource() {
            return source;
        }

        public Node getTarget() {
            return target;
        }

        public String getPredicate() {
            return predicate;
        }

        public int getSupport() {
            return evidence.size();
        }

        public int getDocumentCount() {
            return documents.size();
        }

        public double getConfidence() {
            double best = 0;
            for (Triple triple : evidence) {
                best = Math.max(best, triple.getConfidence());
            }
            return best;
        }

        public List<Triple> getEvidence() {
            return Collections.unmodifiableList(evidence);
        }

        public String getId() {
            return source.getId() + "\t" + predicate + "\t" + target.getId();
        }

        @Override
        public String toString() {
            return source.getLabel() + " --[" + predicate + " x" + getSupport() + "]--> " + target.getLabel();
        }
    }
}
