package org.example.ner;

import java.util.*;

/**
 * Merges the output of several extractors, so that the ontology matcher and BioNER can be run as one extractor.
 */
public class CompositeExtractor implements EntityExtractor {

    private final List<EntityExtractor> extractors;

    /**
     * Initializes a composite over the given extractors.
     * @param extractors Extractors to run, in order.
     */
    public CompositeExtractor(List<EntityExtractor> extractors) {
        this.extractors = extractors;
    }

    @Override
    public List<Entity> extract(String text) {
        ArrayList<Entity> merged = new ArrayList<>();
        for (EntityExtractor extractor : extractors) {
            for (Entity entity : extractor.extract(text)) {
                if (!merged.contains(entity)) {
                    merged.add(entity);
                }
            }
        }
        Collections.sort(merged);
        return merged;
    }

    @Override
    public String name() {
        StringBuilder names = new StringBuilder();
        for (EntityExtractor extractor : extractors) {
            if (names.length() > 0) {
                names.append('+');
            }
            names.append(extractor.name());
        }
        return names.toString();
    }

    @Override
    public void close() {
        for (EntityExtractor extractor : extractors) {
            extractor.close();
        }
    }
}
