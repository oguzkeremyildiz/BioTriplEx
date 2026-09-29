package org.example.ner;

import java.util.*;

/**
 * Finds typed mentions in a piece of text. The dictionary matcher and the transformer model are interchangeable
 * behind this interface, which is what lets the evaluation run the same pipeline over both.
 */
public interface EntityExtractor extends AutoCloseable {

    /**
     * Extracts the mentions of a text.
     * @param text Text to process.
     * @return The mentions found, sorted by position.
     */
    List<Entity> extract(String text);

    /**
     * Short name used in reports and in the user interface.
     * @return The name of the extractor.
     */
    String name();

    @Override
    default void close() {
    }

    /**
     * Runs several extractors over the same text and merges their output.
     * @param extractors Extractors to combine.
     * @return An extractor that returns the union of the mentions the given extractors find.
     */
    static EntityExtractor of(EntityExtractor... extractors) {
        return new CompositeExtractor(Arrays.asList(extractors));
    }
}
