package org.example.ner;

import org.example.Ontology;
import org.example.Term;
import org.example.Type;

import java.util.*;

/**
 * Ontology lookup over DOID and HG_PCO, the deterministic half of the pipeline.
 * Term.findMatches scans one pattern at a time with KMP, which costs O(terms * text) and reports any substring hit,
 * so CEA fires inside CEACAM1. This extractor keeps the same vocabulary but indexes it by surface form and probes only
 * the word n-grams that occur in the text, which is O(text) and matches whole words only.
 */
public final class DictionaryExtractor implements EntityExtractor {

    private static final int MAX_WORDS = 8;
    private static final int MIN_LENGTH = 3;

    private final HashMap<String, Type> caseInsensitive;
    private final HashMap<String, Type> caseSensitive;
    private final boolean longestMatchOnly;
    private int maxWords;

    /**
     * Initializes an extractor that keeps only the longest match at every position.
     */
    public DictionaryExtractor() {
        this(true);
    }

    /**
     * Initializes an extractor.
     * @param longestMatchOnly When true, mentions nested inside a longer mention of the same type are dropped.
     */
    public DictionaryExtractor(boolean longestMatchOnly) {
        this.caseInsensitive = new HashMap<>();
        this.caseSensitive = new HashMap<>();
        this.longestMatchOnly = longestMatchOnly;
        this.maxWords = 1;
    }

    /**
     * Adds the surface forms of an ontology: names, synonyms and, for genes, the symbols stored as ids.
     * @param ontology Ontology to index.
     * @return This extractor, so that ontologies can be chained.
     */
    public DictionaryExtractor add(Ontology ontology) {
        Type type = ontology.getType();
        for (int i = 0; i < ontology.size(); i++) {
            Term term = ontology.getTerm(i);
            addAll(term.get("name"), type, false);
            addAll(term.get("synonym"), type, false);
            if (type == Type.GENE) {
                addAll(term.get("id"), type, true);
            }
        }
        return this;
    }

    /**
     * Adds a single surface form to the index.
     * @param surfaceForm Surface form to add.
     * @param type Type the surface form denotes.
     * @param matchCase When true, the form only matches with the very same capitalization, as gene symbols must.
     * @return This extractor.
     */
    public DictionaryExtractor add(String surfaceForm, Type type, boolean matchCase) {
        if (surfaceForm == null) {
            return this;
        }
        String value = surfaceForm.trim().replaceAll("\\s+", " ");
        if (value.length() < MIN_LENGTH || !hasLetter(value)) {
            return this;
        }
        int words = countWords(value);
        if (words > MAX_WORDS) {
            return this;
        }
        maxWords = Math.max(maxWords, words);
        if (matchCase) {
            caseSensitive.putIfAbsent(value, type);
        } else {
            caseInsensitive.putIfAbsent(value.toLowerCase(Locale.ROOT), type);
        }
        return this;
    }

    private void addAll(List<String> values, Type type, boolean matchCase) {
        for (String value : values) {
            add(value, type, matchCase);
        }
    }

    public int size() {
        return caseInsensitive.size() + caseSensitive.size();
    }

    @Override
    public List<Entity> extract(String text) {
        List<int[]> tokens = tokenize(text);
        ArrayList<Entity> found = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Entity longest = null;
            for (int n = 0; n < maxWords && i + n < tokens.size(); n++) {
                int start = tokens.get(i)[0];
                int end = tokens.get(i + n)[1];
                String candidate = text.substring(start, end);
                Type type = lookup(candidate);
                if (type == null) {
                    continue;
                }
                Entity entity = new Entity(type, candidate, start, end, 1.0, name());
                if (!longestMatchOnly) {
                    found.add(entity);
                } else if (longest == null || entity.length() > longest.length()) {
                    longest = entity;
                }
            }
            if (longest != null) {
                found.add(longest);
            }
        }
        Collections.sort(found);
        return longestMatchOnly ? removeNested(found) : found;
    }

    private Type lookup(String candidate) {
        Type type = caseSensitive.get(candidate);
        if (type != null) {
            return type;
        }
        return caseInsensitive.get(candidate.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    private static List<Entity> removeNested(List<Entity> sorted) {
        ArrayList<Entity> kept = new ArrayList<>(sorted.size());
        for (Entity candidate : sorted) {
            boolean covered = false;
            for (Entity other : sorted) {
                if (other != candidate && other.getType() == candidate.getType() && other.getStart() <= candidate.getStart() && other.getEnd() >= candidate.getEnd() && other.length() > candidate.length()) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    /**
     * Splits a text into word ranges, a word being a run of letters and digits.
     * @param text Text to tokenize.
     * @return The start and end offset of every word.
     */
    static List<int[]> tokenize(String text) {
        ArrayList<int[]> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                int start = i;
                while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
                    i++;
                }
                tokens.add(new int[]{start, i});
            } else {
                i++;
            }
        }
        return tokens;
    }

    private static int countWords(String value) {
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetterOrDigit(value.charAt(i))) {
                if (!inWord) {
                    words++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return words;
    }

    private static boolean hasLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String name() {
        return "Dictionary";
    }
}
