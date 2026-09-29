package org.example;

import org.example.kg.KnowledgeGraph;
import org.example.kg.RelationExtractor;
import org.example.kg.RelationLexicon;
import org.example.kg.Triple;
import org.example.ner.Entity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that triples are only drawn inside a sentence, that gapped trigger patterns fire, and that the graph merges
 * the mentions of one entity into a single node.
 */
public class RelationExtractorTest {

    private static RelationLexicon lexicon() {
        RelationLexicon lexicon = new RelationLexicon();
        lexicon.add("upregulated", "increased expression", 23);
        lexicon.add("high ... expression", "increased expression", 12);
        return lexicon;
    }

    @Test
    public void linksAGeneAndADiseaseThroughATrigger() {
        String text = "TP53 is upregulated in lung cancer.";
        List<Entity> entities = List.of(new Entity(Type.GENE, "TP53", 0, 4), new Entity(Type.DISEASE, "lung cancer", 23, 34));
        List<Triple> triples = new RelationExtractor(lexicon(), RelationExtractor.Mode.TRIGGER_ONLY).extract(text, entities, "test");
        assertEquals(1, triples.size());
        assertEquals("increased expression", triples.get(0).getPredicate());
        assertEquals("upregulated", triples.get(0).getTrigger());
    }

    @Test
    public void matchesAGappedTrigger() {
        String text = "High TP53 expression was seen in lung cancer.";
        List<Entity> entities = List.of(new Entity(Type.GENE, "TP53", 5, 9), new Entity(Type.DISEASE, "lung cancer", 32, 43));
        List<Triple> triples = new RelationExtractor(lexicon(), RelationExtractor.Mode.TRIGGER_ONLY).extract(text, entities, "test");
        assertEquals(1, triples.size());
        assertEquals("increased expression", triples.get(0).getPredicate());
    }

    @Test
    public void doesNotCrossSentenceBoundaries() {
        String text = "TP53 is upregulated. Another study looked at lung cancer.";
        List<Entity> entities = List.of(new Entity(Type.GENE, "TP53", 0, 4), new Entity(Type.DISEASE, "lung cancer", 44, 55));
        assertTrue(new RelationExtractor(lexicon(), RelationExtractor.Mode.TRIGGER_ONLY).extract(text, entities, "test").isEmpty());
    }

    @Test
    public void emitsCoOccurrenceOnlyWhenAsked() {
        String text = "TP53 was measured together with lung cancer stage.";
        List<Entity> entities = List.of(new Entity(Type.GENE, "TP53", 0, 4), new Entity(Type.DISEASE, "lung cancer", 32, 43));
        assertTrue(new RelationExtractor(lexicon(), RelationExtractor.Mode.TRIGGER_ONLY).extract(text, entities, "test").isEmpty());
        List<Triple> loose = new RelationExtractor(lexicon(), RelationExtractor.Mode.WITH_CO_OCCURRENCE).extract(text, entities, "test");
        assertEquals(1, loose.size());
        assertEquals(RelationExtractor.CO_OCCURRENCE_LABEL, loose.get(0).getPredicate());
    }

    @Test
    public void mergesMentionsIntoOneNodeAndCountsSupport() {
        String first = "TP53 is upregulated in lung cancer.";
        String second = "tp53 is upregulated in Lung Cancer.";
        RelationExtractor extractor = new RelationExtractor(lexicon(), RelationExtractor.Mode.TRIGGER_ONLY);
        ArrayList<Triple> triples = new ArrayList<>();
        triples.addAll(extractor.extract(first, List.of(new Entity(Type.GENE, "TP53", 0, 4), new Entity(Type.DISEASE, "lung cancer", 23, 34)), "a.xml"));
        triples.addAll(extractor.extract(second, List.of(new Entity(Type.GENE, "tp53", 0, 4), new Entity(Type.DISEASE, "Lung Cancer", 23, 34)), "b.xml"));
        KnowledgeGraph graph = new KnowledgeGraph();
        graph.addAll(triples);
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
        KnowledgeGraph.Edge edge = graph.getEdges().iterator().next();
        assertEquals(2, edge.getSupport());
        assertEquals(2, edge.getDocumentCount());
        assertEquals(1, graph.filter(2).edgeCount());
        assertEquals(0, graph.filter(3).edgeCount());
    }
}
