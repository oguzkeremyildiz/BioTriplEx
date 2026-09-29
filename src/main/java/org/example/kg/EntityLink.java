package org.example.kg;

/**
 * A gold ENTITY_LINKING annotation. It references a gene tag, a disease tag and the relation tag that connects them by
 * id, which is what the extracted triples are scored against.
 */
public final class EntityLink {

    private final String id;
    private final String geneId;
    private final String geneText;
    private final String diseaseId;
    private final String diseaseText;
    private final String relationId;
    private final String relationText;

    /**
     * Initializes a gold link.
     * @param id Identifier of the link.
     * @param geneId Identifier of the gene tag.
     * @param geneText Surface form of the gene.
     * @param diseaseId Identifier of the disease tag.
     * @param diseaseText Surface form of the disease.
     * @param relationId Identifier of the relation tag.
     * @param relationText Surface form of the relation trigger.
     */
    public EntityLink(String id, String geneId, String geneText, String diseaseId, String diseaseText, String relationId, String relationText) {
        this.id = id;
        this.geneId = geneId;
        this.geneText = geneText;
        this.diseaseId = diseaseId;
        this.diseaseText = diseaseText;
        this.relationId = relationId;
        this.relationText = relationText;
    }

    public String getId() {
        return id;
    }

    public String getGeneId() {
        return geneId;
    }

    public String getGeneText() {
        return geneText;
    }

    public String getDiseaseId() {
        return diseaseId;
    }

    public String getDiseaseText() {
        return diseaseText;
    }

    public String getRelationId() {
        return relationId;
    }

    public String getRelationText() {
        return relationText;
    }

    @Override
    public String toString() {
        return geneText + " --[" + relationText + "]--> " + diseaseText;
    }
}
