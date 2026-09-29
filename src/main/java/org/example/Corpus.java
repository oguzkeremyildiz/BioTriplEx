package org.example;

import java.io.*;
import java.util.*;

/**
 * The annotated BioTriplEx corpus, one directory per paper and one XML file per section.
 * Splits are taken at paper level, never at section level, so that sentences of the same paper cannot end up on both
 * sides of a train and test boundary.
 */
public final class Corpus {

    public static final String DEFAULT_ROOT = "Annotated Full Text Paper Folders-6";

    private final LinkedHashMap<String, List<Xml>> papers;

    private Corpus(LinkedHashMap<String, List<Xml>> papers) {
        this.papers = papers;
    }

    /**
     * Reads every paper of a corpus directory.
     * @param root Directory holding one subdirectory per paper.
     * @return The parsed corpus.
     * @throws FileNotFoundException When the directory does not exist.
     */
    public static Corpus load(File root) throws FileNotFoundException {
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null) {
            throw new FileNotFoundException("Corpus directory not found: " + root.getAbsolutePath());
        }
        Arrays.sort(directories);
        LinkedHashMap<String, List<Xml>> papers = new LinkedHashMap<>();
        for (File directory : directories) {
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml") && !name.toLowerCase().contains("electronic supp"));
            if (files == null || files.length == 0) {
                continue;
            }
            Arrays.sort(files);
            ArrayList<Xml> sections = new ArrayList<>(files.length);
            for (File file : files) {
                Xml document = Xml.load(file);
                if (!document.isEmpty()) {
                    sections.add(document);
                }
            }
            if (!sections.isEmpty()) {
                papers.put(directory.getName(), sections);
            }
        }
        return new Corpus(papers);
    }

    /**
     * Returns every section of every paper, in paper order.
     * @return All sections.
     */
    public List<Xml> documents() {
        ArrayList<Xml> all = new ArrayList<>();
        for (List<Xml> sections : papers.values()) {
            all.addAll(sections);
        }
        return all;
    }

    public int paperCount() {
        return papers.size();
    }

    public int documentCount() {
        return documents().size();
    }

    /**
     * Splits the corpus at paper level, deterministically and without shuffling.
     * @param trainFraction Fraction of the papers that goes to the training side.
     * @return The training corpus and the test corpus.
     */
    public Corpus[] split(double trainFraction) {
        LinkedHashMap<String, List<Xml>> train = new LinkedHashMap<>();
        LinkedHashMap<String, List<Xml>> test = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, List<Xml>> entry : papers.entrySet()) {
            if ((index % 10) / 10.0 < trainFraction) {
                train.put(entry.getKey(), entry.getValue());
            } else {
                test.put(entry.getKey(), entry.getValue());
            }
            index++;
        }
        return new Corpus[]{new Corpus(train), new Corpus(test)};
    }

    @Override
    public String toString() {
        return paperCount() + " papers / " + documentCount() + " sections";
    }
}
