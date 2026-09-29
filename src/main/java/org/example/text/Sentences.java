package org.example.text;

import java.util.*;

/**
 * Sentence segmentation for biomedical text.
 * Sentences are the scope in which a gene and a disease are considered related and they are also the unit the
 * transformer input is packed from, so the splitter returns offsets rather than strings: every returned range is a
 * slice of the original document and keeps the character positions the gold annotations use.
 */
public final class Sentences {

    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList("al", "approx", "ca", "cf", "dr", "e.g", "eg", "et", "etc", "fig", "figs", "i.e", "ie", "min", "mr", "mrs", "ms", "no", "nos", "p", "ph", "prof", "ref", "refs", "resp", "sp", "spp", "st", "vs", "vol", "wt"));

    private Sentences() {
    }

    /**
     * Splits a text into sentence ranges.
     * @param text Text to split.
     * @return The start and exclusive end offset of every sentence, without the surrounding whitespace.
     */
    public static List<int[]> split(String text) {
        ArrayList<int[]> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean boundary = c == '\n' || ((c == '.' || c == '!' || c == '?') && isTerminal(text, i));
            if (boundary) {
                add(sentences, text, start, i + 1);
                start = i + 1;
            }
        }
        add(sentences, text, start, text.length());
        return sentences;
    }

    /**
     * Finds the sentence an offset falls into.
     * @param sentences Sentence ranges as returned by split.
     * @param offset Offset to look up.
     * @return The enclosing range, or null when the offset is in the whitespace between sentences.
     */
    public static int[] enclosing(List<int[]> sentences, int offset) {
        for (int[] sentence : sentences) {
            if (offset >= sentence[0] && offset < sentence[1]) {
                return sentence;
            }
        }
        return null;
    }

    private static void add(List<int[]> sentences, String text, int start, int end) {
        int first = start;
        int last = end;
        while (first < last && Character.isWhitespace(text.charAt(first))) {
            first++;
        }
        while (last > first && Character.isWhitespace(text.charAt(last - 1))) {
            last--;
        }
        if (first < last) {
            sentences.add(new int[]{first, last});
        }
    }

    private static boolean isTerminal(String text, int index) {
        if (index + 1 >= text.length()) {
            return true;
        }
        if (text.charAt(index) == '.') {
            if (index > 0 && Character.isDigit(text.charAt(index - 1)) && Character.isDigit(text.charAt(index + 1))) {
                return false;
            }
            if (isAbbreviation(text, index)) {
                return false;
            }
        }
        if (!Character.isWhitespace(text.charAt(index + 1))) {
            return false;
        }
        int next = index + 1;
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) {
            next++;
        }
        if (next >= text.length()) {
            return true;
        }
        char following = text.charAt(next);
        return Character.isUpperCase(following) || Character.isDigit(following) || following == '"' || following == '(' || following == '[';
    }

    private static boolean isAbbreviation(String text, int index) {
        int start = index;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        String word = text.substring(start, index).toLowerCase(Locale.ROOT);
        return !word.isEmpty() && (word.length() == 1 || ABBREVIATIONS.contains(word));
    }
}
