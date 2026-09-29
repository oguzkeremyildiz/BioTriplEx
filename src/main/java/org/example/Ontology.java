package org.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

/** An OBO ontology: DOID for diseases, HG_PCO for gene symbols. */
public class Ontology implements Iterable<Term> {

    /** Locations of the ontologies shipped with the repository. */
    public static final String DISEASE_OBO = "ontologies&others/doid.obo";
    public static final String GENE_OBO = "ontologies&others/HG_PCO.obo";

    private final List<Term> terms;
    private final Type type;

    public Ontology(File file, Type type) throws FileNotFoundException {
        this.terms = new ArrayList<>();
        this.type = type;
        try (Scanner source = new Scanner(file, "UTF-8")) {
            while (source.hasNextLine()) {
                String line = source.nextLine();
                if (!line.trim().equals("[Term]")) {
                    continue;
                }
                List<String> lines = new ArrayList<>();
                while (source.hasNextLine()) {
                    line = source.nextLine();
                    if (line.isEmpty()) {
                        break;
                    }
                    lines.add(line);
                }
                if (!lines.isEmpty()) {
                    terms.add(new Term(lines, type));
                }
            }
        }
    }

    public Ontology(List<Term> terms, Type type) {
        this.terms = terms;
        this.type = type;
    }

    /** Loads the ontology that belongs to {@code type} from its default location. */
    public static Ontology forType(Type type) throws FileNotFoundException {
        return new Ontology(new File(type == Type.GENE ? GENE_OBO : DISEASE_OBO), type);
    }

    public Type getType() {
        return type;
    }

    public int size() {
        return terms.size();
    }

    public Term getTerm(int index) {
        return terms.get(index);
    }

    @Override
    public Iterator<Term> iterator() {
        return Collections.unmodifiableList(terms).iterator();
    }

    @Override
    public String toString() {
        return type + " ontology (" + terms.size() + " terms)";
    }
}
