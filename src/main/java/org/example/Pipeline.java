package org.example;

import org.example.kg.KnowledgeGraph;
import org.example.kg.RelationExtractor;
import org.example.kg.RelationLexicon;
import org.example.kg.Triple;
import org.example.ner.BioNerExtractor;
import org.example.ner.DictionaryExtractor;
import org.example.ner.Entity;
import org.example.ner.EntityExtractor;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Text in, knowledge graph out: ingestion, then entity extraction, then relation extraction, then the graph. The
 * entity step sits behind the EntityExtractor interface so that the ontology matcher and BioNER run through the same
 * downstream code and can be compared directly.
 */
public final class Pipeline implements AutoCloseable {

    private final Method method;
    private final EntityExtractor extractor;
    private final RelationExtractor relationExtractor;

    /**
     * Initializes a pipeline from its parts.
     * @param method Method the extractor implements, used for reporting.
     * @param extractor Entity extractor to run.
     * @param relationExtractor Relation extractor to run.
     */
    public Pipeline(Method method, EntityExtractor extractor, RelationExtractor relationExtractor) {
        this.method = method;
        this.extractor = extractor;
        this.relationExtractor = relationExtractor;
    }

    /**
     * Builds a pipeline with the relation lexicon that ships with the application.
     * @param method Extraction method to use.
     * @param progress Receives progress lines, may be null.
     * @return The pipeline.
     * @throws IOException When an extractor cannot be created.
     */
    public static Pipeline create(Method method, Consumer<String> progress) throws IOException {
        return create(method, RelationLexicon.loadDefault(), RelationExtractor.Mode.TRIGGER_ONLY, progress);
    }

    /**
     * Builds a pipeline.
     * @param method Extraction method to use.
     * @param lexicon Relation trigger vocabulary.
     * @param mode What to do with pairs that share a sentence but no trigger.
     * @param progress Receives progress lines, may be null.
     * @return The pipeline.
     * @throws IOException When an extractor cannot be created.
     */
    public static Pipeline create(Method method, RelationLexicon lexicon, RelationExtractor.Mode mode, Consumer<String> progress) throws IOException {
        return new Pipeline(method, createExtractor(method, progress), new RelationExtractor(lexicon, mode));
    }

    /**
     * Creates the entity extractor of a method.
     * @param method Extraction method to use.
     * @param progress Receives progress lines, may be null.
     * @return The extractor.
     * @throws IOException When the ontologies or the models cannot be read.
     */
    public static EntityExtractor createExtractor(Method method, Consumer<String> progress) throws IOException {
        switch (method) {
            case DICTIONARY:
                return dictionary(progress);
            case BIONER:
                return bioNer(progress);
            case BOTH:
                return EntityExtractor.of(dictionary(progress), bioNer(progress));
            default:
                throw new IllegalArgumentException("Unknown method: " + method);
        }
    }

    private static EntityExtractor dictionary(Consumer<String> progress) throws IOException {
        report(progress, "Loading ontologies (DOID, HG_PCO)...");
        DictionaryExtractor dictionary = new DictionaryExtractor();
        dictionary.add(new Ontology(new File(Ontology.DISEASE_OBO), Type.DISEASE));
        dictionary.add(new Ontology(new File(Ontology.GENE_OBO), Type.GENE));
        report(progress, "Indexed " + dictionary.size() + " surface forms.");
        return dictionary;
    }

    private static EntityExtractor bioNer(Consumer<String> progress) throws IOException {
        return EntityExtractor.of(BioNerExtractor.load(Type.DISEASE, progress), BioNerExtractor.load(Type.GENE, progress));
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }

    /**
     * Extracts the entities and the triples of a text.
     * @param name Name of the text, recorded as provenance on the triples.
     * @param text Text to process.
     * @return The result of the run.
     */
    public Result run(String name, String text) {
        List<Entity> entities = extractor.extract(text);
        return new Result(name, text, entities, relationExtractor.extract(text, entities, name));
    }

    /**
     * Extracts the entities and the triples of a document.
     * @param document Document to process.
     * @return The result of the run.
     */
    public Result run(Xml document) {
        return run(document.getName(), document.getText());
    }

    /**
     * Runs every section of a corpus and merges the triples into one graph.
     * @param corpus Corpus to process.
     * @param progress Receives progress lines, may be null.
     * @return The merged graph.
     */
    public KnowledgeGraph buildGraph(Corpus corpus, Consumer<String> progress) {
        KnowledgeGraph graph = new KnowledgeGraph();
        List<Xml> documents = corpus.documents();
        long started = System.currentTimeMillis();
        for (int i = 0; i < documents.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                report(progress, "Cancelled after " + i + " of " + documents.size() + " sections.");
                return graph;
            }
            graph.addAll(run(documents.get(i)).getTriples());
            if (progress != null && (i + 1) % 10 == 0) {
                long elapsed = System.currentTimeMillis() - started;
                long remaining = elapsed * (documents.size() - i - 1) / (i + 1) / 1000;
                report(progress, (i + 1) + " / " + documents.size() + " sections, about " + remaining / 60 + " min " + remaining % 60 + " s left");
            }
        }
        return graph;
    }

    public Method getMethod() {
        return method;
    }

    public EntityExtractor getExtractor() {
        return extractor;
    }

    public RelationExtractor getRelationExtractor() {
        return relationExtractor;
    }

    @Override
    public void close() {
        extractor.close();
    }

    /**
     * Which entity extractor the pipeline runs.
     */
    public enum Method {

        DICTIONARY("Exact match (ontology)"),
        BIONER("BioNER (BioBERT)"),
        BOTH("Ontology + BioNER");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * The entities and the triples of a single text.
     */
    public static final class Result {

        private final String name;
        private final String text;
        private final List<Entity> entities;
        private final List<Triple> triples;

        Result(String name, String text, List<Entity> entities, List<Triple> triples) {
            this.name = name;
            this.text = text;
            this.entities = entities;
            this.triples = triples;
        }

        public String getName() {
            return name;
        }

        public String getText() {
            return text;
        }

        public List<Entity> getEntities() {
            return Collections.unmodifiableList(entities);
        }

        public List<Triple> getTriples() {
            return Collections.unmodifiableList(triples);
        }
    }
}
