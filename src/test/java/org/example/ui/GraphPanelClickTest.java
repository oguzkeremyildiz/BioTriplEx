package org.example.ui;

import org.example.Type;
import org.example.kg.KnowledgeGraph;
import org.example.kg.Triple;
import org.example.ner.Entity;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.view.util.InteractiveElement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clicking a node has to select it. The first implementation routed clicks through a GraphStream ViewerPipe that had
 * to be pumped from a timer while the layout thread mutated the same graph, and a single exception in that pump killed
 * every later click without a word. This test sends a real mouse event at the pixel a node is drawn on and checks that
 * the panel selected it.
 */
public class GraphPanelClickTest {

    @Test
    public void clickingANodeSelectsIt() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        KnowledgeGraph model = new KnowledgeGraph();
        model.add(triple("TP53", "lung cancer"));
        model.add(triple("MDM2", "lung cancer"));
        model.add(triple("MDM2", "breast cancer"));
        GraphPanel panel = new GraphPanel();
        JFrame frame = new JFrame("click test");
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setSize(900, 700);
                frame.setLayout(new BorderLayout());
                frame.add(panel, BorderLayout.CENTER);
                frame.setVisible(true);
                panel.display(model, 20);
            });
            Thread.sleep(1500);
            assertNull(panel.getSelectedNodeId(), "nothing should be selected before a click");
            String clicked = clickWhenReady(panel, 0);
            assertNotNull(clicked, "no node could be hit on the view " + describe(panel));
            assertEquals(clicked, panel.getSelectedNodeId());
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                panel.stop();
                frame.dispose();
            });
        }
    }

    /**
     * Sends a mouse press to the pixel the first node is drawn on, retrying while the force directed layout is still
     * moving the nodes around and none of them sits inside the view yet.
     * @param panel Panel to click.
     * @return The id of the node that was under the cursor, or null when none could be located in time.
     * @throws Exception When the event cannot be dispatched.
     */
    private static String clickWhenReady(GraphPanel panel, int offset) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            ViewPanelHolder holder = new ViewPanelHolder(offset);
            SwingUtilities.invokeAndWait(() -> holder.find(panel));
            if (holder.id != null) {
                return holder.id;
            }
            Thread.sleep(500);
        }
        return null;
    }

    /**
     * A press a few pixels beside a node still has to select it. Nodes are drawn smaller than the mouse cursor, so
     * demanding the exact pixel meant most clicks fell through to GraphStream's selection rectangle and the details
     * pane never filled, which is what the graph tab looked like in use.
     * @throws Exception When the window cannot be driven.
     */
    @Test
    public void clickingNextToANodeStillSelectsIt() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        KnowledgeGraph model = new KnowledgeGraph();
        model.add(triple("TP53", "lung cancer"));
        model.add(triple("MDM2", "lung cancer"));
        GraphPanel panel = new GraphPanel();
        JFrame frame = new JFrame("near click test");
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setSize(900, 700);
                frame.setLayout(new BorderLayout());
                frame.add(panel, BorderLayout.CENTER);
                frame.setVisible(true);
                panel.display(model, 20);
            });
            Thread.sleep(1500);
            assertNotNull(clickWhenReady(panel, 9), "a press 9 pixels from a node should still select it " + describe(panel));
            assertNotNull(panel.getSelectedNodeId(), "the near miss selected nothing");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                panel.stop();
                frame.dispose();
            });
        }
    }

    /**
     * Locates a node on the view and presses the mouse on it, on the event dispatch thread.
     */
    private static final class ViewPanelHolder {

        private final int offset;
        private String id;

        ViewPanelHolder(int offset) {
            this.offset = offset;
        }

        void find(GraphPanel panel) {
            org.graphstream.ui.swing_viewer.ViewPanel view = panel.getView();
            if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
                return;
            }
            // The camera only learns the graph bounds while the renderer draws, and a test window that never comes to
            // the front may never be painted, which leaves every hit test empty. Painting it once primes the camera.
            BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            view.paint(graphics);
            graphics.dispose();
            for (int x = 0; x < view.getWidth(); x += 4) {
                for (int y = 0; y < view.getHeight(); y += 4) {
                    GraphicElement element = view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), x, y);
                    if (element == null) {
                        continue;
                    }
                    int pressX = x + offset;
                    int pressY = y + offset;
                    if (offset != 0 && view.findGraphicElementAt(EnumSet.of(InteractiveElement.NODE), pressX, pressY) != null) {
                        continue;
                    }
                    view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, pressX, pressY, 1, false, MouseEvent.BUTTON1));
                    id = element.getId();
                    return;
                }
            }
        }
    }

    private static String describe(GraphPanel panel) {
        org.graphstream.ui.swing_viewer.ViewPanel view = panel.getView();
        if (view == null) {
            return "(the panel has no view)";
        }
        return "(view " + view.getWidth() + "x" + view.getHeight() + ", showing " + view.isShowing() + ")";
    }

    private static Triple triple(String gene, String disease) {
        return new Triple(new Entity(Type.GENE, gene, 0, gene.length()), "increased expression", new Entity(Type.DISEASE, disease, 20, 20 + disease.length()), "upregulated", "evidence sentence", "test.xml", 1.0);
    }
}
