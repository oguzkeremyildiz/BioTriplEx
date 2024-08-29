package org.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class Ontology {

    private final ArrayList<Term> terms;
    private final Type type;

    public Ontology(File file, Type type) throws FileNotFoundException {
        Scanner source = new Scanner(file);
        this.terms = new ArrayList<>();
        this.type = type;
        while (source.hasNext()) {
            String line = source.nextLine();
            if (line.equals("[Term]")) {
                ArrayList<String> lines = new ArrayList<>();
                line = source.nextLine();
                while (!line.isEmpty()) {
                    lines.add(line);
                    line = source.nextLine();
                }
                terms.add(new Term(lines, type));
            }
        }
        source.close();
    }

    public Ontology(ArrayList<Term> terms, Type type) {
        this.terms = terms;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    private void addTerm(Term term) {
        terms.add(term);
    }

    public int size() {
        return terms.size();
    }

    public Term getTerm(int index) {
        return terms.get(index);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Term term : terms) {
            sb.append(term.toString()).append("\n");
        }
        return sb.toString();
    }
}
