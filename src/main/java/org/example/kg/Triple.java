package org.example.kg;

import org.example.ner.Entity;

/**
 * The core unit of the knowledge graph: Subject, a gene node, then Predicate, a relation edge, then Object, a disease
 * node, together with the sentence it was read from so that every edge stays traceable back to the text.
 */
public final class Triple {

    private final Entity subject;
    private final String predicate;
    private final Entity object;
    private final String trigger;
    private final String evidence;
    private final String document;
    private final double confidence;

    /**
     * Initializes a triple.
     * @param subject Gene mention.
     * @param predicate Relation type.
     * @param object Disease mention.
     * @param trigger Words in the sentence that signalled the relation, empty for pure co-occurrence.
     * @param evidence Sentence the triple was extracted from.
     * @param document Name of the source document.
     * @param confidence Confidence between 0 and 1.
     */
    public Triple(Entity subject, String predicate, Entity object, String trigger, String evidence, String document, double confidence) {
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.trigger = trigger;
        this.evidence = evidence;
        this.document = document;
        this.confidence = confidence;
    }

    public Entity getSubject() {
        return subject;
    }

    public String getPredicate() {
        return predicate;
    }

    public Entity getObject() {
        return object;
    }

    public String getTrigger() {
        return trigger;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getDocument() {
        return document;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return subject.getText() + " --[" + predicate + "]--> " + object.getText();
    }
}
