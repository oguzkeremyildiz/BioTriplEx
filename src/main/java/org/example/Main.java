package org.example;

import org.example.eval.CorpusEvaluator;
import org.example.kg.GraphExporter;
import org.example.kg.KnowledgeGraph;
import org.example.kg.RelationLexicon;
import org.example.ui.BioTriplExFrame;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Command line front end. The commands are ui, evaluate, graph and lexicon; running without arguments opens the
 * desktop application.
 */
public class Main {

    /**
     * Dispatches a command.
     * @param args Command name followed by --key value options.
     * @throws Exception When the command fails.
     */
    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "ui" : args[0].toLowerCase(Locale.ROOT);
        Map<String, String> options = parseOptions(args);
        switch (command) {
            case "ui":
                BioTriplExFrame.main(new String[0]);
                break;
            case "evaluate":
                evaluate(options);
                break;
            case "graph":
                graph(options);
                break;
            case "lexicon":
                lexicon(options);
                break;
            case "help":
            case "--help":
            case "-h":
                usage();
                break;
            default:
                System.err.println("Unknown command: " + command);
                usage();
                System.exit(2);
                break;
        }
    }

    private static void usage() {
        System.out.println("BioTriplEx, knowledge graphs from annotated biomedical XML");
        System.out.println();
        System.out.println("  ui                                                              launch the desktop application");
        System.out.println("  evaluate [--methods dictionary,bioner,both] [--corpus DIR] [--out DIR] [--train 0.7] [--on test]");
        System.out.println("                                                                  score extraction against the gold tags");
        System.out.println("  graph [--method bioner] [--min-support N] [--corpus DIR] [--out DIR]");
        System.out.println("                                                                  build and export the knowledge graph");
        System.out.println("  lexicon [--out FILE] [--train 0.7]                              re-mine the relation trigger lexicon");
    }

    private static void evaluate(Map<String, String> options) throws Exception {
        Corpus corpus = Corpus.load(new File(options.getOrDefault("corpus", Corpus.DEFAULT_ROOT)));
        Path out = Paths.get(options.getOrDefault("out", "out"));
        Files.createDirectories(out);
        System.out.println("Corpus: " + corpus);
        CorpusEvaluator evaluator = new CorpusEvaluator(corpus, Double.parseDouble(options.getOrDefault("train", "0.7")), System.out);
        System.out.println("Relation lexicon: " + evaluator.getLexicon().size() + " triggers mined from " + evaluator.getTrain());
        Corpus target = "train".equalsIgnoreCase(options.getOrDefault("on", "test")) ? evaluator.getTrain() : evaluator.getTest();
        ArrayList<CorpusEvaluator.Report> reports = new ArrayList<>();
        for (String name : options.getOrDefault("methods", "dictionary,bioner,both").split(",")) {
            reports.add(evaluator.evaluate(method(name.trim()), target));
        }
        Path summary = out.resolve("evaluation.md");
        CorpusEvaluator.writeSummary(reports, summary, corpus, target);
        System.out.println();
        System.out.println("Summary written to " + summary);
    }

    private static void graph(Map<String, String> options) throws Exception {
        Corpus corpus = Corpus.load(new File(options.getOrDefault("corpus", Corpus.DEFAULT_ROOT)));
        Path out = Paths.get(options.getOrDefault("out", "out"));
        int minimumSupport = Integer.parseInt(options.getOrDefault("min-support", "2"));
        Pipeline.Method method = method(options.getOrDefault("method", "bioner"));
        System.out.println("Corpus: " + corpus);
        System.out.println("Method: " + method.getLabel());
        try (Pipeline pipeline = Pipeline.create(method, System.out::println)) {
            long started = System.currentTimeMillis();
            KnowledgeGraph graph = pipeline.buildGraph(corpus, System.out::println);
            KnowledgeGraph filtered = graph.filter(minimumSupport);
            System.out.println("Raw graph      : " + graph);
            System.out.println("Support >= " + minimumSupport + "  : " + filtered);
            System.out.println("Built in " + (System.currentTimeMillis() - started) / 1000.0 + " s");
            GraphExporter.writeAll(filtered, out, "knowledge-graph");
            org.example.ui.GraphPanel.writeImage(filtered, org.example.ui.GraphPanel.DEFAULT_MAX_NODES, out.resolve("knowledge-graph.png"));
            System.out.println();
            System.out.println("Top hubs:");
            for (KnowledgeGraph.Node node : filtered.hubs(10)) {
                System.out.println("  " + node + ", degree " + filtered.degree(node));
            }
            System.out.println();
            System.out.println("Exported to " + out.toAbsolutePath());
        }
    }

    private static void lexicon(Map<String, String> options) throws Exception {
        Corpus corpus = Corpus.load(new File(options.getOrDefault("corpus", Corpus.DEFAULT_ROOT)));
        Corpus train = corpus.split(Double.parseDouble(options.getOrDefault("train", "0.7")))[0];
        RelationLexicon lexicon = RelationLexicon.fromCorpus(train, 1);
        Path file = Paths.get(options.getOrDefault("out", "src/main/resources/relation-lexicon.tsv"));
        lexicon.save(file);
        System.out.println("Mined " + lexicon.size() + " triggers from " + train);
        System.out.println("Written to " + file.toAbsolutePath());
    }

    private static Pipeline.Method method(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "dictionary":
            case "kmp":
            case "exact":
                return Pipeline.Method.DICTIONARY;
            case "bioner":
            case "biobert":
            case "ml":
                return Pipeline.Method.BIONER;
            case "both":
            case "hybrid":
                return Pipeline.Method.BOTH;
            default:
                throw new IllegalArgumentException("Unknown method: " + name);
        }
    }

    /**
     * Parses the --key value and --key=value pairs of a command line.
     * @param args Arguments, the command itself at index zero.
     * @return The options.
     */
    private static Map<String, String> parseOptions(String[] args) {
        LinkedHashMap<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                continue;
            }
            String key = args[i].substring(2);
            int equals = key.indexOf('=');
            if (equals >= 0) {
                options.put(key.substring(0, equals), key.substring(equals + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                i++;
                options.put(key, args[i]);
            } else {
                options.put(key, "true");
            }
        }
        return options;
    }
}
