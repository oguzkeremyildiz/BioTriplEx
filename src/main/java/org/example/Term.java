package org.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One {@code [Term]} stanza of an OBO ontology: an id, a name and any number of synonyms.
 *
 * <p>{@link #findMatches} is the original Knuth-Morris-Pratt matcher from the CS&nbsp;401 baseline.
 * It is kept as the reference implementation &mdash; {@code DictionaryTest} checks the fast
 * {@link org.example.ner.DictionaryExtractor} against it &mdash; but the pipeline itself uses the
 * indexed matcher, because running one KMP pass per term costs {@code O(terms * text)}.
 */
public class Term {

    private final Map<String, List<String>> attributeMap;
    private final Type type;

    public Term(List<String> lines, Type type) {
        this.type = type;
        this.attributeMap = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator);
            String value;
            if (!key.equals("synonym")) {
                value = line.substring(separator + 1).trim();
            } else {
                // synonym: "hemangiosarcoma" EXACT []
                int open = line.indexOf('"');
                int close = line.lastIndexOf('"');
                if (open < 0 || close <= open) {
                    continue;
                }
                value = line.substring(open + 1, close);
            }
            if (value.isEmpty()) {
                continue;
            }
            attributeMap.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
    }

    /** Values of an OBO attribute ({@code name}, {@code synonym}, {@code id}), never null. */
    public List<String> get(String key) {
        return attributeMap.getOrDefault(key, Collections.emptyList());
    }

    public String getFirst(String key) {
        List<String> values = get(key);
        return values.isEmpty() ? null : values.get(0);
    }

    public String getId() {
        return getFirst("id");
    }

    public String getName() {
        return getFirst("name");
    }

    public Type getType() {
        return type;
    }

    private static void computeLPSArray(String pat, int m, int[] lps) {
        int len = 0;
        lps[0] = 0;
        int i = 1;
        while (i < m) {
            if (pat.charAt(i) == pat.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
    }

    /**
     * Knuth-Morris-Pratt search. Returns the zero based start offsets of every occurrence of
     * {@code pat} in {@code txt}, on the same coordinate system as the gold {@code spans}.
     */
    public static List<Integer> search(String pat, String txt) {
        List<Integer> result = new ArrayList<>();
        int m = pat.length();
        int n = txt.length();
        if (m == 0 || m > n) {
            return result;
        }
        int[] lps = new int[m];
        computeLPSArray(pat, m, lps);
        int i = 0;
        int j = 0;
        while ((n - i) >= (m - j)) {
            if (pat.charAt(j) == txt.charAt(i)) {
                j++;
                i++;
            }
            if (j == m) {
                result.add(i - j);
                j = lps[j - 1];
            } else if (i < n && pat.charAt(j) != txt.charAt(i)) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i = i + 1;
                }
            }
        }
        return result;
    }

    private void addMatches(Map<String, List<Integer>> matches, List<String> list, String text) {
        for (String key : list) {
            List<Integer> match = search(key, text);
            if (!match.isEmpty()) {
                matches.put(key, match);
            }
        }
    }

    /** Surface form to offsets, for every surface form of this term found in {@code text}. */
    public Map<String, List<Integer>> findMatches(String text) {
        Map<String, List<Integer>> matches = new HashMap<>();
        switch (type) {
            case GENE:
                addMatches(matches, get("id"), text);
                addMatches(matches, get("name"), text);
                break;
            case DISEASE:
                addMatches(matches, get("synonym"), text);
                addMatches(matches, get("name"), text);
                break;
            default:
                break;
        }
        return matches;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : attributeMap.entrySet()) {
            for (String value : entry.getValue()) {
                sb.append(entry.getKey()).append(" -> ").append(value).append("\n");
            }
        }
        return sb.toString();
    }
}
