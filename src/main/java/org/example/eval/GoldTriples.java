package org.example.eval;

import org.example.Xml;
import org.example.kg.EntityLink;
import org.example.kg.RelationMention;
import org.example.kg.Triple;
import org.example.ner.Entity;

import java.util.*;

/**
 * Resolves the ENTITY_LINKING annotations of a document into triples, so that the ground truth and the extracted
 * output have the same shape and can be scored against each other.
 */
public final class GoldTriples {

    private GoldTriples() {
    }

    /**
     * Builds the gold triples of a document.
     * @param document Annotated document.
     * @return The gold triples, skipping links whose endpoints are missing.
     */
    public static List<Triple> of(Xml document) {
        ArrayList<Triple> triples = new ArrayList<>();
        for (EntityLink link : document.getLinks()) {
            Entity gene = document.getEntityById(link.getGeneId());
            Entity disease = document.getEntityById(link.getDiseaseId());
            if (gene == null || disease == null) {
                continue;
            }
            RelationMention relation = document.getRelationById(link.getRelationId());
            String predicate = relation == null ? "related_to" : relation.getLabel();
            String trigger = relation == null ? link.getRelationText() : relation.getText();
            triples.add(new Triple(gene, predicate, disease, trigger, "", document.getName(), 1.0));
        }
        return triples;
    }
}
