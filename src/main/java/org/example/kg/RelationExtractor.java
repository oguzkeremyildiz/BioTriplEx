package org.example.kg;

import org.example.Type;
import org.example.ner.Entity;
import org.example.text.Sentences;

import java.util.*;

/**
 * Turns a list of mentions into Subject, Predicate, Object triples.
 * The scope is the sentence: in the corpus 1134 of the 1199 gold links keep the gene, the disease and the relation
 * trigger inside one sentence, so a pair that spans a sentence boundary is more likely to be a coincidence than a
 * statement. Inside a sentence every gene is paired with every disease and the pair is labelled with the trigger from
 * the relation lexicon that lies closest to both mentions.
 */
public final class RelationExtractor {

    public static final String CO_OCCURRENCE_LABEL = "co-occurrence";

    private static final String DISTANCE_PROPERTY = "biotriplex.relation.maxDistance";
    private static final int DEFAULT_MAX_TRIGGER_DISTANCE = 120;

    private final RelationLexicon lexicon;
    private final Mode mode;
    private final int maxTriggerDistance;

    /**
     * Initializes a relation extractor with the default trigger distance.
     * @param lexicon Trigger vocabulary to label the pairs with.
     * @param mode What to do with a pair that shares a sentence but no trigger.
     */
    public RelationExtractor(RelationLexicon lexicon, Mode mode) {
        this(lexicon, mode, Integer.getInteger(DISTANCE_PROPERTY, DEFAULT_MAX_TRIGGER_DISTANCE));
    }

    /**
     * Initializes a relation extractor.
     * @param lexicon Trigger vocabulary to label the pairs with.
     * @param mode What to do with a pair that shares a sentence but no trigger.
     * @param maxTriggerDistance Furthest a trigger may sit from both mentions and still describe the pair.
     */
    public RelationExtractor(RelationLexicon lexicon, Mode mode, int maxTriggerDistance) {
        this.lexicon = lexicon;
        this.mode = mode;
        this.maxTriggerDistance = maxTriggerDistance;
    }

    public RelationLexicon getLexicon() {
        return lexicon;
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Extracts the triples of a text.
     * @param text Text the mentions were found in.
     * @param entities Mentions found by an extractor.
     * @param documentName Provenance recorded on every triple.
     * @return The extracted triples.
     */
    public List<Triple> extract(String text, List<Entity> entities, String documentName) {
        ArrayList<Triple> triples = new ArrayList<>();
        for (int[] range : Sentences.split(text)) {
            String sentence = text.substring(range[0], range[1]);
            List<Entity> genes = within(entities, range, Type.GENE);
            List<Entity> diseases = within(entities, range, Type.DISEASE);
            if (genes.isEmpty() || diseases.isEmpty()) {
                continue;
            }
            List<RelationLexicon.Match> matches = lexicon.findAll(sentence);
            for (Entity gene : genes) {
                for (Entity disease : diseases) {
                    Triple triple = link(gene, disease, matches, sentence, range[0], documentName);
                    if (triple != null) {
                        triples.add(triple);
                    }
                }
            }
        }
        return triples;
    }

    private Triple link(Entity gene, Entity disease, List<RelationLexicon.Match> matches, String sentence, int sentenceStart, String documentName) {
        int geneOffset = gene.getStart() - sentenceStart;
        int diseaseOffset = disease.getStart() - sentenceStart;
        RelationLexicon.Match best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (RelationLexicon.Match match : matches) {
            int distance = match.distanceTo(geneOffset) + match.distanceTo(diseaseOffset);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = match;
            }
        }
        if (best == null || bestDistance > maxTriggerDistance) {
            if (mode == Mode.TRIGGER_ONLY) {
                return null;
            }
            return new Triple(gene, CO_OCCURRENCE_LABEL, disease, "", sentence, documentName, 0.3 * gene.getConfidence() * disease.getConfidence());
        }
        double proximity = 1.0 - Math.min(1.0, bestDistance / (double) maxTriggerDistance);
        double confidence = (0.5 + 0.5 * proximity) * gene.getConfidence() * disease.getConfidence();
        return new Triple(gene, best.getLabel(), disease, best.getText(), sentence, documentName, confidence);
    }

    private static List<Entity> within(List<Entity> entities, int[] range, Type type) {
        ArrayList<Entity> selected = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity.getType() == type && entity.getStart() >= range[0] && entity.getEnd() <= range[1]) {
                selected.add(entity);
            }
        }
        return selected;
    }

    /**
     * What to do with a gene and a disease that share a sentence but no trigger phrase.
     */
    public enum Mode {

        TRIGGER_ONLY,
        WITH_CO_OCCURRENCE
    }
}
