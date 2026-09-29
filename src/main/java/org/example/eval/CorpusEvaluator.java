package org.example.eval;

import org.example.Corpus;
import org.example.Pipeline;
import org.example.Score;
import org.example.Type;
import org.example.Xml;
import org.example.kg.RelationExtractor;
import org.example.kg.RelationLexicon;
import org.example.kg.Triple;
import org.example.ner.Entity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Runs the pipeline over the annotated corpus and reports precision, recall and F1 for entity extraction and for
 * triple extraction. The relation lexicon is learnt on the training papers only and every number comes from the held
 * out papers, so the relation scores are not measured on the sentences the triggers were read from.
 */
public final class CorpusEvaluator {

    private final Corpus train;
    private final Corpus test;
    private final RelationLexicon lexicon;
    private final PrintStream out;

    /**
     * Initializes an evaluator and mines the relation lexicon from the training split.
     * @param corpus Corpus to evaluate on.
     * @param trainFraction Fraction of the papers used for mining the lexicon.
     * @param out Stream the progress and the reports are written to.
     */
    public CorpusEvaluator(Corpus corpus, double trainFraction, PrintStream out) {
        Corpus[] split = corpus.split(trainFraction);
        this.train = split[0];
        this.test = split[1];
        this.lexicon = RelationLexicon.fromCorpus(train, 1);
        this.out = out;
    }

    public RelationLexicon getLexicon() {
        return lexicon;
    }

    public Corpus getTrain() {
        return train;
    }

    public Corpus getTest() {
        return test;
    }

    /**
     * Evaluates one extraction method on the held out papers.
     * @param method Method to evaluate.
     * @return The report of the run.
     * @throws IOException When the extractor cannot be created.
     */
    public Report evaluate(Pipeline.Method method) throws IOException {
        return evaluate(method, test);
    }

    /**
     * Evaluates one extraction method on a chosen part of the corpus. Thresholds are tuned on the training part and
     * only the held out part is reported.
     * @param method Method to evaluate.
     * @param target Corpus to score on.
     * @return The report of the run.
     * @throws IOException When the extractor cannot be created.
     */
    public Report evaluate(Pipeline.Method method, Corpus target) throws IOException {
        out.println();
        out.println("=== " + method.getLabel() + " ===");
        out.println("Scored on: " + target);
        long started = System.currentTimeMillis();
        try (Pipeline pipeline = Pipeline.create(method, lexicon, RelationExtractor.Mode.TRIGGER_ONLY, out::println)) {
            Report report = new Report(method);
            List<Xml> documents = target.documents();
            int done = 0;
            for (Xml document : documents) {
                Pipeline.Result result = pipeline.run(document);
                List<Entity> gold = document.getEntities();
                report.strict.add(SpanMatcher.match(gold, result.getEntities(), SpanMatcher.Mode.STRICT).getScore());
                report.overlap.add(SpanMatcher.match(gold, result.getEntities(), SpanMatcher.Mode.OVERLAP).getScore());
                for (Type type : new Type[]{Type.GENE, Type.DISEASE}) {
                    report.perType.computeIfAbsent(type, t -> new Score()).add(SpanMatcher.match(gold, result.getEntities(), SpanMatcher.Mode.STRICT, type).getScore());
                }
                List<Triple> goldTriples = GoldTriples.of(document);
                report.triplePairs.add(TripleMatcher.match(goldTriples, result.getTriples(), TripleMatcher.Mode.PAIR).getScore());
                report.tripleLabelled.add(TripleMatcher.match(goldTriples, result.getTriples(), TripleMatcher.Mode.LABELLED).getScore());
                report.predictedTriples += result.getTriples().size();
                report.goldTriples += goldTriples.size();
                done++;
                if (done % 50 == 0) {
                    out.println("  " + done + " / " + documents.size() + " sections");
                }
            }
            report.milliseconds = System.currentTimeMillis() - started;
            report.print(out);
            return report;
        }
    }

    /**
     * Writes the comparison table of several reports.
     * @param reports Reports to tabulate.
     * @param file File to write.
     * @param corpus Corpus the reports were produced on.
     * @param test Held out part of the corpus.
     * @throws IOException When the file cannot be written.
     */
    public static void writeSummary(List<Report> reports, Path file, Corpus corpus, Corpus test) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("# BioTriplEx evaluation");
        lines.add("");
        lines.add("Corpus: " + corpus + ". Held out test set: " + test + ".");
        lines.add("Entity scores are micro averaged over sections, triple scores match endpoints by span overlap.");
        lines.add("The relation lexicon was mined from the training papers only.");
        lines.add("");
        lines.add("| Method                 | P     | R     | F1    | F1 ov | Pair  | Label | Time s |");
        lines.add("|------------------------|-------|-------|-------|-------|-------|-------|--------|");
        for (Report report : reports) {
            lines.add(report.row());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /**
     * The metrics of one method.
     */
    public static final class Report {

        private final Pipeline.Method method;
        private final Score strict;
        private final Score overlap;
        private final LinkedHashMap<Type, Score> perType;
        private final Score triplePairs;
        private final Score tripleLabelled;
        private int predictedTriples;
        private int goldTriples;
        private long milliseconds;

        Report(Pipeline.Method method) {
            this.method = method;
            this.strict = new Score();
            this.overlap = new Score();
            this.perType = new LinkedHashMap<>();
            this.triplePairs = new Score();
            this.tripleLabelled = new Score();
        }

        public Pipeline.Method getMethod() {
            return method;
        }

        public Score getStrict() {
            return strict;
        }

        public Score getOverlap() {
            return overlap;
        }

        public Score getTriplePairs() {
            return triplePairs;
        }

        public Score getTripleLabelled() {
            return tripleLabelled;
        }

        public long getMilliseconds() {
            return milliseconds;
        }

        /**
         * Prints the report.
         * @param out Stream to print to.
         */
        public void print(PrintStream out) {
            out.println();
            out.println("Entities, exact span : " + strict);
            out.println("Entities, overlap    : " + overlap);
            for (Map.Entry<Type, Score> entry : perType.entrySet()) {
                out.printf("  %-18s : %s%n", entry.getKey(), entry.getValue());
            }
            out.println("Triples, pair only   : " + triplePairs);
            out.println("Triples, with label  : " + tripleLabelled);
            out.println("Triples predicted    : " + predictedTriples + " (gold " + goldTriples + ")");
            out.println("Runtime              : " + milliseconds / 1000.0 + " s");
        }

        /**
         * Formats the report as one row of the comparison table.
         * @return The table row.
         */
        public String row() {
            return String.format(Locale.ROOT, "| %-22s | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %6.1f |", method.getLabel(), strict.getPrecision(), strict.getRecall(), strict.getF1(), overlap.getF1(), triplePairs.getF1(), tripleLabelled.getF1(), milliseconds / 1000.0);
        }
    }
}
