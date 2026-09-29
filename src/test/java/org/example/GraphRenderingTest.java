package org.example;

import org.example.kg.KnowledgeGraph;
import org.example.kg.Triple;
import org.example.ner.Entity;
import org.example.ui.GraphPanel;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders a graph through the same GraphStream renderer and stylesheet the window uses. It caught two real defects:
 * the renderer rejecting a second predicate between the same two nodes, and an empty picture when nothing is drawn.
 */
public class GraphRenderingTest {

    @Test
    public void rendersAGraphThatHasSeveralPredicatesBetweenTwoNodes() throws Exception {
        KnowledgeGraph graph = new KnowledgeGraph();
        graph.add(triple("TP53", "increased expression", "lung cancer"));
        graph.add(triple("TP53", "therapeutic target", "lung cancer"));
        graph.add(triple("MDM2", "increased expression", "lung cancer"));
        graph.add(triple("MDM2", "causative mutation", "breast cancer"));
        Path file = Files.createTempFile("biotriplex-graph", ".png");
        try {
            GraphPanel.writeImage(graph, 50, file);
            assertTrue(Files.size(file) > 0, "no image was written");
            BufferedImage image = ImageIO.read(file.toFile());
            assertNotNull(image);
            assertTrue(image.getWidth() > 100 && image.getHeight() > 100);
            assertTrue(colours(image) > 1, "the rendered image is a single flat colour, nothing was drawn");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static Triple triple(String gene, String predicate, String disease) {
        return new Triple(new Entity(Type.GENE, gene, 0, gene.length()), predicate, new Entity(Type.DISEASE, disease, 20, 20 + disease.length()), "trigger", "evidence sentence", "test.xml", 1.0);
    }

    private static int colours(BufferedImage image) {
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int x = 0; x < image.getWidth(); x += 3) {
            for (int y = 0; y < image.getHeight(); y += 3) {
                seen.add(image.getRGB(x, y));
                if (seen.size() > 8) {
                    return seen.size();
                }
            }
        }
        return seen.size();
    }
}
