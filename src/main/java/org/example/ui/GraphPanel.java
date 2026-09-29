package org.example.ui;

import org.example.kg.KnowledgeGraph;
import org.example.kg.Triple;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.stream.file.FileSinkImages;
import org.graphstream.stream.file.images.Resolutions;
import org.graphstream.ui.swing.util.SwingFileSinkImages;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicNode;
import org.graphstream.ui.view.camera.Camera;
import org.graphstream.ui.swing_viewer.SwingViewer;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Interactive rendering of the knowledge graph with GraphStream: a force directed layout, genes and diseases in
 * different colours, node size by degree, and a details pane that shows the sentences behind whatever is clicked.
 * The renderer is a MultiGraph rather than a SingleGraph because one gene and one disease can be joined by several
 * predicates at once, and a SingleGraph rejects the second of them.
 */
public class GraphPanel extends JPanel {

    public static final int DEFAULT_MAX_NODES = 150;

    /** How far from the cursor a node may sit and still count as clicked, in pixels. */
    private static final int CLICK_TOLERANCE = 24;

    private static final String STYLE_SHEET = "graph { fill-color: #FFFFFF; padding: 40px; } node { size: 14px; text-size: 12; text-alignment: at-right; text-offset: 4, 0; text-padding: 2; text-background-mode: rounded-box; text-background-color: #FFFFFFCC; stroke-mode: plain; stroke-color: #FFFFFF; } node.gene { fill-color: #1C7293; } node.disease { fill-color: #B85042; } node.selected { stroke-mode: plain; stroke-width: 3px; stroke-color: #21295C; text-style: bold; } edge { fill-color: #9AA5B1; arrow-size: 8px, 4px; text-size: 10; text-color: #56626F; text-background-mode: rounded-box; text-background-color: #FFFFFFCC; } edge.strong { fill-color: #4A5568; size: 2px; }";

    private final JTextArea details;
    private final JLabel caption;
    private KnowledgeGraph model;
    private Graph graph;
    private SwingViewer viewer;
    private ViewPanel view;
    private JSplitPane split;
    private String selected;

    /**
     * Initializes an empty panel.
     */
    public GraphPanel() {
        super(new BorderLayout());
        System.setProperty("org.graphstream.ui", "swing");
        this.details = new JTextArea("Click a node to see the relations and the sentences behind them.");
        this.caption = new JLabel(" ");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        caption.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        caption.setForeground(new Color(90, 90, 90));
        add(caption, BorderLayout.NORTH);
    }

    /**
     * Replaces the rendered graph.
     * @param knowledgeGraph Graph to render.
     * @param maxNodes Number of best connected nodes to draw, the rest are left out.
     */
    public void display(KnowledgeGraph knowledgeGraph, int maxNodes) {
        this.model = knowledgeGraph;
        stop();
        if (split != null) {
            remove(split);
            split = null;
        }
        selected = null;
        int[] hidden = new int[1];
        graph = build(knowledgeGraph, maxNodes, hidden);
        caption.setText("Showing the " + graph.getNodeCount() + " best connected of " + knowledgeGraph.nodeCount() + " nodes, " + hidden[0] + " edges hidden with them. Scroll to zoom, drag to pan, click a node for details.");
        viewer = new SwingViewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
        viewer.enableAutoLayout();
        view = (ViewPanel) viewer.addDefaultView(false);
        listen(view);
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, view, new JScrollPane(details));
        split.setResizeWeight(0.75);
        add(split, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Builds the renderable graph: the best connected nodes, the edges between them, and the styling.
     * @param knowledgeGraph Graph to render.
     * @param maxNodes Number of best connected nodes to keep.
     * @param hidden Single element array that receives the number of edges left out with the hidden nodes.
     * @return The GraphStream graph.
     */
    private static Graph build(KnowledgeGraph knowledgeGraph, int maxNodes, int[] hidden) {
        MultiGraph graph = new MultiGraph("BioTriplEx");
        graph.setAttribute("ui.stylesheet", STYLE_SHEET);
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");
        List<KnowledgeGraph.Node> visible = knowledgeGraph.hubs(maxNodes);
        HashSet<String> visibleIds = new HashSet<>();
        for (KnowledgeGraph.Node node : visible) {
            visibleIds.add(node.getId());
        }
        for (KnowledgeGraph.Node node : visible) {
            Node rendered = graph.addNode(node.getId());
            rendered.setAttribute("ui.label", node.getLabel());
            rendered.setAttribute("ui.class", styleOf(node));
            rendered.setAttribute("ui.style", "size: " + Math.min(44, 16 + 2 * knowledgeGraph.degree(node)) + "px;");
        }
        for (KnowledgeGraph.Edge edge : knowledgeGraph.getEdges()) {
            if (!visibleIds.contains(edge.getSource().getId()) || !visibleIds.contains(edge.getTarget().getId())) {
                hidden[0]++;
                continue;
            }
            if (graph.getEdge(edge.getId()) != null) {
                continue;
            }
            org.graphstream.graph.Edge rendered = graph.addEdge(edge.getId(), edge.getSource().getId(), edge.getTarget().getId(), true);
            if (rendered == null) {
                continue;
            }
            rendered.setAttribute("ui.label", edge.getPredicate() + " (" + edge.getSupport() + ")");
            if (edge.getSupport() > 1) {
                rendered.setAttribute("ui.class", "strong");
            }
        }
        return graph;
    }

    /**
     * Renders a knowledge graph straight to a PNG file, with the same renderer and stylesheet the window uses but
     * without opening one. This is what the export writes, and it is how the rendering is checked in a test.
     * @param knowledgeGraph Graph to render.
     * @param maxNodes Number of best connected nodes to draw.
     * @param file File to write.
     * @throws IOException When the file cannot be written.
     */
    public static void writeImage(KnowledgeGraph knowledgeGraph, int maxNodes, Path file) throws IOException {
        System.setProperty("org.graphstream.ui", "swing");
        Graph graph = build(knowledgeGraph, maxNodes, new int[1]);
        SwingFileSinkImages images = new SwingFileSinkImages(FileSinkImages.OutputType.PNG, Resolutions.HD1080);
        images.setLayoutPolicy(FileSinkImages.LayoutPolicy.COMPUTED_FULLY_AT_NEW_IMAGE);
        images.setStyleSheet(STYLE_SHEET);
        images.writeAll(graph, file.toString());
    }

    /**
     * Makes the nodes selectable by hit testing the view where the mouse went down.
     * The GraphStream ViewerPipe was the obvious route, but it only delivers a click after pump() is called from
     * another thread while the layout thread is mutating the same graph, and one exception in that pump silently ends
     * every later click. Asking the view directly what sits under the cursor runs on the event dispatch thread, needs
     * no pumping and cannot race the layout.
     * @param panel The view to listen on.
     */
    private void listen(ViewPanel panel) {
        panel.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent event) {
                selectAt(event.getX(), event.getY());
            }
        });
    }

    /**
     * Selects the node drawn at a point of the view, if there is one.
     * A node is drawn 14 pixels wide, so demanding a hit on the exact pixel means the user has to aim at a dot smaller
     * than the mouse cursor and every near miss falls through to the selection rectangle instead. The point is
     * therefore tried first, then rings of increasing radius around it, and the nearest node within the tolerance wins.
     * @param x Horizontal position in the view.
     * @param y Vertical position in the view.
     */
    private void selectAt(int x, int y) {
        if (view == null || viewer == null) {
            return;
        }
        try {
            String nearest = nearestNode(x, y);
            if (nearest != null) {
                select(nearest);
            }
        } catch (RuntimeException ignored) {
            System.out.println("The graph view is still laying out, ignoring the click.");
        }
    }

    /**
     * Finds the node drawn closest to a point of the view, within {@link #CLICK_TOLERANCE} pixels.
     * GraphStream offers findGraphicElementAt for this, but its answer disagrees with what the renderer actually drew:
     * on a screen where the graphics context carries a scaling transform, a node painted at one position is reported
     * as empty space, so every click fell through to the selection rectangle and the details pane never filled.
     * Projecting the node coordinates with the same camera the renderer places them with, and taking the nearest, is
     * both independent of that and more forgiving than demanding a hit on a fourteen pixel dot.
     * @param x Horizontal position of the click in the view.
     * @param y Vertical position of the click in the view.
     * @return The id of the nearest node, or null when none is close enough.
     */
    private String nearestNode(int x, int y) {
        Camera camera = view.getCamera();
        double scale = cameraScale(camera);
        double targetX = x * scale;
        double targetY = y * scale;
        double tolerance = CLICK_TOLERANCE * scale;
        String nearest = null;
        double best = tolerance * tolerance;
        for (org.graphstream.graph.Node node : viewer.getGraphicGraph()) {
            GraphicNode drawn = (GraphicNode) node;
            if (!drawn.positionned) {
                continue;
            }
            Point3 pixels = camera.transformGuToPx(drawn.getX(), drawn.getY(), 0);
            double dx = pixels.x - targetX;
            double dy = pixels.y - targetY;
            double distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                nearest = drawn.getId();
            }
        }
        return nearest;
    }

    /**
     * How many camera pixels there are per Swing pixel.
     * On a high resolution screen the renderer is handed a graphics context that already scales everything by two, and
     * the camera then keeps its projection in those device pixels while a mouse event still reports Swing pixels. A
     * node drawn at (529, 39) is reported by the camera at (1060, 80), so comparing the two directly misses every node
     * by a factor of two, which is why clicks only ever produced a selection rectangle on such a screen.
     * The factor is measured rather than assumed: the centre of the camera view has to project onto the centre of the
     * component, so the ratio between the two gives the scale, and it is one on an ordinary screen.
     * @param camera Camera of the view.
     * @return The scale factor, between one and four.
     */
    private double cameraScale(Camera camera) {
        if (view.getWidth() <= 0) {
            return 1.0;
        }
        Point3 centre = camera.getViewCenter();
        Point3 projected = camera.transformGuToPx(centre.x, centre.y, 0);
        double scale = projected.x / (view.getWidth() / 2.0);
        return Math.min(4.0, Math.max(1.0, scale));
    }

    private void select(String nodeId) {
        if (graph == null || model == null) {
            return;
        }
        if (selected != null) {
            Node previous = graph.getNode(selected);
            KnowledgeGraph.Node old = model.getNode(selected);
            if (previous != null && old != null) {
                previous.setAttribute("ui.class", styleOf(old));
            }
        }
        selected = nodeId;
        Node rendered = graph.getNode(nodeId);
        KnowledgeGraph.Node node = model.getNode(nodeId);
        if (rendered == null || node == null) {
            return;
        }
        rendered.setAttribute("ui.class", styleOf(node) + ", selected");
        details.setText(describe(node));
        details.setCaretPosition(0);
    }

    private String describe(KnowledgeGraph.Node node) {
        StringBuilder text = new StringBuilder();
        text.append(node.getLabel()).append("  [").append(node.getType()).append(", ").append(node.getMentions()).append(" mentions]\n\n");
        ArrayList<KnowledgeGraph.Edge> edges = new ArrayList<>(model.edgesOf(node));
        edges.sort((a, b) -> Integer.compare(b.getSupport(), a.getSupport()));
        for (KnowledgeGraph.Edge edge : edges) {
            text.append(edge).append('\n');
            List<Triple> evidence = edge.getEvidence();
            for (int i = 0; i < Math.min(2, evidence.size()); i++) {
                text.append("    ").append(evidence.get(i).getDocument()).append(": ").append(shorten(evidence.get(i).getEvidence())).append('\n');
            }
        }
        return text.toString();
    }

    private static String styleOf(KnowledgeGraph.Node node) {
        return node.getType() == org.example.Type.GENE ? "gene" : "disease";
    }

    private static String shorten(String sentence) {
        String cleaned = sentence.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 220 ? cleaned : cleaned.substring(0, 217) + "...";
    }

    /**
     * The node the user last clicked.
     * @return Its id, or null when nothing is selected.
     */
    public String getSelectedNodeId() {
        return selected;
    }

    /**
     * The view the graph is drawn on, so that a test can send it a real mouse event.
     * @return The view, or null before the first display.
     */
    ViewPanel getView() {
        return view;
    }

    /**
     * Stops the layout thread, called when the window closes.
     */
    public void stop() {
        view = null;
        if (viewer != null) {
            try {
                viewer.close();
            } catch (RuntimeException ignored) {
                System.out.println("The graph viewer was already closed.");
            }
            viewer = null;
        }
    }
}
