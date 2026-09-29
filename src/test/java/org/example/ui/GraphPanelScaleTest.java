package org.example.ui;

import org.example.Type;
import org.example.kg.KnowledgeGraph;
import org.example.kg.Triple;
import org.example.ner.Entity;
import org.graphstream.ui.swing_viewer.ViewPanel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * On a high resolution screen the renderer draws into a surface twice the size of the component, so the camera holds a
 * viewport in device pixels while a mouse event carries Swing pixels. Hit testing with the raw coordinates then probes
 * at half the position the user aimed at: every click misses, GraphStream falls back to its selection rectangle, and
 * the details pane never fills. That is exactly what the graph tab did on a Retina display while the plain window in
 * the other tests worked.
 * This test paints the view at twice the scale, which is what a Retina paint does, and then clicks with ordinary Swing
 * coordinates.
 */
public class GraphPanelScaleTest {

    @Test
    public void clicksLandOnNodesWhenTheViewIsRenderedAtTwiceTheScale() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        KnowledgeGraph model = new KnowledgeGraph();
        model.add(triple("TP53", "lung cancer"));
        model.add(triple("MDM2", "lung cancer"));
        model.add(triple("MDM2", "breast cancer"));
        GraphPanel panel = new GraphPanel();
        JFrame frame = new JFrame("scale test");
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame.setSize(900, 700);
                frame.setLayout(new BorderLayout());
                frame.add(panel, BorderLayout.CENTER);
                frame.setVisible(true);
                panel.display(model, 20);
            });
            Thread.sleep(2000);
            String selected = clickAfterRetinaPaint(panel);
            assertNotNull(selected, "no node was selected after a two times scaled paint");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                panel.stop();
                frame.dispose();
            });
        }
    }

    /**
     * Renders the view at twice the scale, then presses the mouse at the Swing coordinate of a node.
     * @param panel Panel to drive.
     * @return The selected node id, or null when nothing was selected.
     * @throws Exception When the window cannot be driven.
     */
    private static String clickAfterRetinaPaint(GraphPanel panel) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            String[] result = new String[1];
            SwingUtilities.invokeAndWait(() -> result[0] = pressOnANode(panel));
            if (result[0] != null) {
                return result[0];
            }
            Thread.sleep(500);
        }
        return null;
    }

    private static String pressOnANode(GraphPanel panel) {
        ViewPanel view = panel.getView();
        if (view == null || view.getWidth() <= 0) {
            return null;
        }
        // Draw the way a high resolution screen does: a surface twice the size, with a doubling transform.
        BufferedImage surface = new BufferedImage(view.getWidth() * 2, view.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = surface.createGraphics();
        graphics.scale(2, 2);
        view.paint(graphics);
        graphics.dispose();
        // Find where a node is painted, in the coordinates the user's mouse reports.
        for (int x = 0; x < view.getWidth(); x += 2) {
            for (int y = 0; y < view.getHeight(); y += 2) {
                int rgb = surface.getRGB(Math.min(x * 2, surface.getWidth() - 1), Math.min(y * 2, surface.getHeight() - 1)) & 0xFFFFFF;
                if (rgb != 0x1C7293 && rgb != 0xB85042) {
                    continue;
                }
                view.dispatchEvent(new MouseEvent(view, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
                return panel.getSelectedNodeId();
            }
        }
        return null;
    }

    private static Triple triple(String gene, String disease) {
        return new Triple(new Entity(Type.GENE, gene, 0, gene.length()), "increased expression", new Entity(Type.DISEASE, disease, 20, 20 + disease.length()), "upregulated", "evidence sentence", "test.xml", 1.0);
    }
}
