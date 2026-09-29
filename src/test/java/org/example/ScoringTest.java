package org.example;

import org.example.eval.GoldTriples;
import org.example.eval.SpanMatcher;
import org.example.eval.TripleMatcher;
import org.example.kg.Triple;
import org.example.ner.Entity;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the matchers used by the evaluation: the strict and overlapping span alignment, and the pair and labelled
 * triple alignment.
 */
public class ScoringTest {

    @Test
    public void strictMatchingNeedsTheExactSpan() {
        List<Entity> gold = List.of(new Entity(Type.DISEASE, "lung cancer", 10, 21));
        List<Entity> predicted = List.of(new Entity(Type.DISEASE, "cancer", 15, 21, 0.9, "test"));
        assertEquals(0, SpanMatcher.match(gold, predicted, SpanMatcher.Mode.STRICT).getScore().getTP());
        assertEquals(1, SpanMatcher.match(gold, predicted, SpanMatcher.Mode.OVERLAP).getScore().getTP());
    }

    @Test
    public void aGoldMentionIsConsumedOnlyOnce() {
        List<Entity> gold = List.of(new Entity(Type.DISEASE, "lung cancer", 10, 21));
        List<Entity> predicted = List.of(new Entity(Type.DISEASE, "lung", 10, 14, 0.9, "test"), new Entity(Type.DISEASE, "cancer", 15, 21, 0.9, "test"));
        Score score = SpanMatcher.match(gold, predicted, SpanMatcher.Mode.OVERLAP).getScore();
        assertEquals(1, score.getTP());
        assertEquals(1, score.getFP());
        assertEquals(0, score.getFN());
    }

    @Test
    public void typesHaveToAgree() {
        List<Entity> gold = List.of(new Entity(Type.DISEASE, "TP53", 0, 4));
        List<Entity> predicted = List.of(new Entity(Type.GENE, "TP53", 0, 4, 0.9, "test"));
        assertEquals(0, SpanMatcher.match(gold, predicted, SpanMatcher.Mode.OVERLAP).getScore().getTP());
    }

    @Test
    public void perfectPredictionScoresOne() {
        List<Entity> gold = List.of(new Entity(Type.GENE, "TP53", 0, 4), new Entity(Type.DISEASE, "cancer", 10, 16));
        Score score = SpanMatcher.match(gold, gold, SpanMatcher.Mode.STRICT).getScore();
        assertEquals(1.0, score.getPrecision(), 1e-9);
        assertEquals(1.0, score.getRecall(), 1e-9);
        assertEquals(1.0, score.getF1(), 1e-9);
    }

    @Test
    public void tripleMatchingComparesEndpointsThenLabels() {
        Entity gene = new Entity(Type.GENE, "TP53", 0, 4);
        Entity disease = new Entity(Type.DISEASE, "lung cancer", 20, 31);
        List<Triple> gold = List.of(new Triple(gene, "increased expression", disease, "upregulated", "", "a.xml", 1.0));
        Entity wider = new Entity(Type.GENE, "TP53 protein", 0, 12, 0.9, "test");
        List<Triple> predicted = List.of(new Triple(wider, "therapeutic target", disease, "target", "", "a.xml", 0.7));
        assertEquals(1, TripleMatcher.match(gold, predicted, TripleMatcher.Mode.PAIR).getScore().getTP());
        assertEquals(0, TripleMatcher.match(gold, predicted, TripleMatcher.Mode.LABELLED).getScore().getTP());
    }

    @Test
    public void resolvesTheGoldLinksOfADocument() throws Exception {
        Xml document = new Xml(new File(Corpus.DEFAULT_ROOT + "/2807459/2807459_ABSTRACT.xml"));
        List<Triple> gold = GoldTriples.of(document);
        assertEquals(4, gold.size());
        assertEquals("dysregulation", gold.get(0).getPredicate());
        assertEquals(Type.GENE, gold.get(0).getSubject().getType());
        assertEquals(Type.DISEASE, gold.get(0).getObject().getType());
    }
}
