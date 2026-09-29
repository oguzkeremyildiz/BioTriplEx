package org.example.kg;

/**
 * A relation trigger, the phrase in the text that states how a gene and a disease are connected, such as
 * "deregulated" or "elevated ... expression". In the corpus these are the RELATION tags.
 */
public final class RelationMention {

    private final String id;
    private final String text;
    private final String label;
    private final int start;
    private final int end;

    /**
     * Initializes a relation trigger.
     * @param id Identifier the ENTITY_LINKING tags refer to.
     * @param text Surface form of the trigger.
     * @param label Normalized relation type, such as dysregulation or increased expression.
     * @param start Inclusive start offset.
     * @param end Exclusive end offset.
     */
    public RelationMention(String id, String text, String label, int start, int end) {
        this.id = id;
        this.text = text;
        this.label = label;
        this.start = start;
        this.end = end;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getLabel() {
        return label;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return label + "(" + text + ")";
    }
}
