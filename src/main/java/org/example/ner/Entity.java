package org.example.ner;

import org.example.Type;

import java.util.*;

/**
 * A typed mention of a biomedical entity, addressed by character offsets into the text of a document.
 * Offsets follow the convention of the gold annotations: index 0 is the first character of the CDATA block
 * inside TEXT, so a predicted entity and a spans="start~end" attribute can be compared directly.
 */
public final class Entity implements Comparable<Entity> {

    public static final String GOLD = "gold";

    private final Type type;
    private final String text;
    private final int start;
    private final int end;
    private final double confidence;
    private final String source;

    /**
     * Initializes a gold entity with full confidence.
     * @param type Type of the mention.
     * @param text Surface form of the mention.
     * @param start Inclusive start offset.
     * @param end Exclusive end offset.
     */
    public Entity(Type type, String text, int start, int end) {
        this(type, text, start, end, 1.0, GOLD);
    }

    /**
     * Initializes an entity found by an extractor.
     * @param type Type of the mention.
     * @param text Surface form of the mention.
     * @param start Inclusive start offset.
     * @param end Exclusive end offset.
     * @param confidence Extractor confidence between 0 and 1.
     * @param source Name of the extractor that found the mention.
     */
    public Entity(Type type, String text, int start, int end, double confidence, String source) {
        this.type = type;
        this.text = text;
        this.start = start;
        this.end = end;
        this.confidence = confidence;
        this.source = source;
    }

    /**
     * Creates an entity whose span is stripped of surrounding whitespace, so that it lines up with the gold annotations.
     * @param type Type of the mention.
     * @param document Text the offsets refer to.
     * @param start Inclusive start offset.
     * @param end Exclusive end offset.
     * @param confidence Extractor confidence between 0 and 1.
     * @param source Name of the extractor that found the mention.
     * @return The trimmed entity, or null when the span holds nothing but whitespace.
     */
    public static Entity trimmed(Type type, String document, int start, int end, double confidence, String source) {
        int first = start;
        int last = end;
        while (first < last && Character.isWhitespace(document.charAt(first))) {
            first++;
        }
        while (last > first && Character.isWhitespace(document.charAt(last - 1))) {
            last--;
        }
        if (first >= last) {
            return null;
        }
        return new Entity(type, document.substring(first, last), first, last, confidence, source);
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int length() {
        return end - start;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getSource() {
        return source;
    }

    /**
     * Case and whitespace insensitive form used to merge mentions into a single graph node.
     * @return The normalized surface form.
     */
    public String normalized() {
        return normalize(text);
    }

    /**
     * Lowercases the value and collapses its whitespace.
     * @param value Value to normalize.
     * @return The normalized value.
     */
    public static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * Identifier of the graph node this mention belongs to.
     * @return The node id.
     */
    public String nodeId() {
        return type + ":" + normalized();
    }

    /**
     * Checks whether both mentions have the same type and intersecting character spans.
     * @param other The other mention.
     * @return True when the mentions overlap.
     */
    public boolean overlaps(Entity other) {
        return type == other.type && start < other.end && other.start < end;
    }

    @Override
    public int compareTo(Entity other) {
        if (start != other.start) {
            return Integer.compare(start, other.start);
        }
        if (end != other.end) {
            return Integer.compare(other.end, end);
        }
        return type.compareTo(other.type);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Entity)) {
            return false;
        }
        Entity other = (Entity) o;
        return start == other.start && end == other.end && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, start, end);
    }

    @Override
    public String toString() {
        return type + "[" + start + "~" + end + "] " + text;
    }
}
