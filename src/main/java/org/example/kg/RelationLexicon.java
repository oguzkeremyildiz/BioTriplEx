package org.example.kg;

import org.example.Corpus;
import org.example.Xml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * The phrases that signal a gene and disease relation, and the relation type each one implies.
 * The vocabulary is learnt from the RELATION annotations of the corpus rather than hand written: every trigger is
 * counted with the labels it was annotated with and keeps the majority label. Discontinuous annotations such as
 * "high ... expression" become a two part pattern whose halves must both occur, in order, inside the same sentence.
 */
public final class RelationLexicon {

    private static final String NO_RELATION = "no relation";
    private static final String RESOURCE = "/relation-lexicon.tsv";
    private static final String GAP = "...";

    private final ArrayList<Trigger> triggers;

    public RelationLexicon() {
        this.triggers = new ArrayList<>();
    }

    /**
     * Adds a trigger to the lexicon and keeps the lexicon ordered from the most specific pattern to the least.
     * @param pattern Trigger pattern, with "..." marking a gap.
     * @param label Relation type the trigger implies.
     * @param count Number of times the trigger was annotated.
     */
    public void add(String pattern, String label, int count) {
        triggers.add(new Trigger(pattern, label, count));
        triggers.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
    }

    public List<Trigger> getTriggers() {
        return Collections.unmodifiableList(triggers);
    }

    public int size() {
        return triggers.size();
    }

    /**
     * Finds every trigger occurring in a sentence.
     * @param sentence Sentence to scan.
     * @return The matches, the most specific pattern first, with sentence relative offsets.
     */
    public List<Match> findAll(String sentence) {
        String lowercase = sentence.toLowerCase(Locale.ROOT);
        ArrayList<Match> matches = new ArrayList<>();
        for (Trigger trigger : triggers) {
            int[] span = trigger.find(lowercase);
            if (span != null) {
                matches.add(new Match(trigger, span[0], span[1], sentence.substring(span[0], span[1])));
            }
        }
        return matches;
    }

    /**
     * Learns a lexicon from the RELATION tags of a corpus.
     * @param corpus Corpus to mine, normally the training split only.
     * @param minimumCount Least number of annotations a trigger needs to be kept.
     * @return The mined lexicon, without the triggers whose majority label is "no relation".
     */
    public static RelationLexicon fromCorpus(Corpus corpus, int minimumCount) {
        HashMap<String, HashMap<String, Integer>> counts = new HashMap<>();
        for (Xml document : corpus.documents()) {
            for (RelationMention relation : document.getRelations()) {
                String pattern = normalizePattern(relation.getText());
                if (!pattern.isEmpty()) {
                    counts.computeIfAbsent(pattern, k -> new HashMap<>()).merge(relation.getLabel(), 1, Integer::sum);
                }
            }
        }
        RelationLexicon lexicon = new RelationLexicon();
        for (Map.Entry<String, HashMap<String, Integer>> entry : counts.entrySet()) {
            String bestLabel = null;
            int bestCount = 0;
            int total = 0;
            for (Map.Entry<String, Integer> label : entry.getValue().entrySet()) {
                total += label.getValue();
                if (label.getValue() > bestCount) {
                    bestCount = label.getValue();
                    bestLabel = label.getKey();
                }
            }
            if (total >= minimumCount && bestLabel != null && !NO_RELATION.equals(bestLabel)) {
                lexicon.add(entry.getKey(), bestLabel, total);
            }
        }
        return lexicon;
    }

    private static String normalizePattern(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * Writes the lexicon in the trigger, label, count tab separated format.
     * @param file File to write.
     * @throws IOException When the file cannot be written.
     */
    public void save(Path file) throws IOException {
        ArrayList<String> lines = new ArrayList<>(triggers.size());
        for (Trigger trigger : triggers) {
            lines.add(trigger.getPattern() + '\t' + trigger.getLabel() + '\t' + trigger.getCount());
        }
        Collections.sort(lines);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * Reads a lexicon from a file.
     * @param file File to read.
     * @return The lexicon.
     * @throws IOException When the file cannot be read.
     */
    public static RelationLexicon load(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    /**
     * Reads the lexicon that ships with the application.
     * @return The default lexicon, empty when the resource is missing.
     */
    public static RelationLexicon loadDefault() {
        try (InputStream in = RelationLexicon.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new RelationLexicon();
            }
            return read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    private static RelationLexicon read(BufferedReader reader) throws IOException {
        RelationLexicon lexicon = new RelationLexicon();
        String line = reader.readLine();
        while (line != null) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] fields = line.split("\t");
                if (fields.length >= 2) {
                    lexicon.add(fields[0], fields[1], fields.length > 2 ? Integer.parseInt(fields[2].trim()) : 1);
                }
            }
            line = reader.readLine();
        }
        return lexicon;
    }

    /**
     * A trigger phrase, possibly split into two parts by a gap.
     */
    public static final class Trigger {

        private final String pattern;
        private final String[] parts;
        private final String label;
        private final int count;

        Trigger(String pattern, String label, int count) {
            this.pattern = pattern;
            this.parts = pattern.split(java.util.regex.Pattern.quote(GAP), -1);
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
            }
            this.label = label;
            this.count = count;
        }

        public String getPattern() {
            return pattern;
        }

        public String getLabel() {
            return label;
        }

        public int getCount() {
            return count;
        }

        int weight() {
            int length = 0;
            for (String part : parts) {
                length += part.length();
            }
            return length;
        }

        /**
         * Finds this trigger inside a lowercased sentence, requiring every part to occur in order.
         * @param lowercaseSentence Sentence in lower case.
         * @return The start and end offset of the match, or null.
         */
        int[] find(String lowercaseSentence) {
            int start = indexOfWord(lowercaseSentence, parts[0], 0);
            if (start < 0) {
                return null;
            }
            int end = start + parts[0].length();
            for (int i = 1; i < parts.length; i++) {
                int next = indexOfWord(lowercaseSentence, parts[i], end);
                if (next < 0) {
                    return null;
                }
                end = next + parts[i].length();
            }
            return new int[]{start, end};
        }

        private static int indexOfWord(String haystack, String needle, int from) {
            if (needle.isEmpty()) {
                return -1;
            }
            int at = haystack.indexOf(needle, from);
            while (at >= 0) {
                boolean leftOk = at == 0 || !Character.isLetterOrDigit(haystack.charAt(at - 1));
                int after = at + needle.length();
                boolean rightOk = after >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(after));
                if (leftOk && rightOk) {
                    return at;
                }
                at = haystack.indexOf(needle, at + 1);
            }
            return -1;
        }
    }

    /**
     * A trigger located in a sentence.
     */
    public static final class Match {

        private final Trigger trigger;
        private final int start;
        private final int end;
        private final String text;

        Match(Trigger trigger, int start, int end, String text) {
            this.trigger = trigger;
            this.start = start;
            this.end = end;
            this.text = text;
        }

        public Trigger getTrigger() {
            return trigger;
        }

        public String getLabel() {
            return trigger.getLabel();
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public String getText() {
            return text;
        }

        /**
         * Measures how far this trigger is from an offset in the sentence.
         * @param offset Offset to measure to.
         * @return The distance in characters, zero when the offset falls inside the trigger.
         */
        public int distanceTo(int offset) {
            if (offset < start) {
                return start - offset;
            }
            if (offset > end) {
                return offset - end;
            }
            return 0;
        }
    }
}
