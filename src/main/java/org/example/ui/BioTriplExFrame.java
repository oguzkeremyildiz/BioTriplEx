package org.example.ui;

import org.example.Corpus;
import org.example.Pipeline;
import org.example.Xml;
import org.example.eval.GoldTriples;
import org.example.eval.SpanMatcher;
import org.example.eval.TripleMatcher;
import org.example.kg.KnowledgeGraph;
import org.example.kg.Triple;
import org.example.ner.Entity;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * The BioTriplEx desktop application: load a section of the corpus or paste a text, extract entities with the ontology
 * matcher or with BioNER, read off the triples, explore the knowledge graph, and score everything against the gold
 * annotations when they are present.
 */
public class BioTriplExFrame extends JFrame {

    private static final org.example.Type GENE = org.example.Type.GENE;
    private static final org.example.Type DISEASE = org.example.Type.DISEASE;
    private static final Color GENE_COLOUR = new Color(0xCC, 0xE4, 0xEC);
    private static final Color DISEASE_COLOUR = new Color(0xF6, 0xD8, 0xD4);

    private final JComboBox<Pipeline.Method> methodBox;
    private final JButton loadButton;
    private final JButton extractButton;
    private final JButton cancelButton;
    private final JLabel status;
    private final JProgressBar progress;
    private final JTextPane textPane;
    private final JTextArea logArea;
    private final JTextArea evaluationArea;
    private final DefaultTableModel entityModel;
    private final DefaultTableModel tripleModel;
    private final GraphPanel graphPanel;
    private final JTabbedPane tabs;
    private final EnumMap<Pipeline.Method, Pipeline> pipelines;
    private Xml loadedDocument;
    private Thread worker;
    private boolean running;

    /**
     * Builds the window.
     */
    public BioTriplExFrame() {
        this.methodBox = new JComboBox<>(Pipeline.Method.values());
        this.loadButton = new JButton("Open XML...");
        this.extractButton = new JButton("Extract");
        this.cancelButton = new JButton("Cancel");
        this.status = new JLabel("Ready.");
        this.progress = new JProgressBar();
        this.textPane = new JTextPane();
        this.logArea = new JTextArea();
        this.evaluationArea = new JTextArea("Open a section of the annotated corpus to score the extraction against its gold tags.");
        this.entityModel = readOnlyModel(new String[]{"Text", "Type", "Start", "End", "Confidence", "Source"});
        this.tripleModel = readOnlyModel(new String[]{"Gene", "Relation", "Disease", "Confidence", "Trigger", "Document"});
        this.graphPanel = new GraphPanel();
        this.tabs = new JTabbedPane();
        this.pipelines = new EnumMap<>(Pipeline.Method.class);
        setTitle("BioTriplEx, BioNER and Knowledge Graph Extraction");
        setSize(1180, 820);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent event) {
                shutdown();
            }
        });
    }

    private JPanel buildToolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));
        loadButton.addActionListener(e -> chooseFile());
        extractButton.addActionListener(e -> runExtraction());
        cancelButton.addActionListener(e -> cancel());
        cancelButton.setEnabled(false);
        panel.add(new JLabel("Entity extraction:"));
        panel.add(methodBox);
        panel.add(loadButton);
        panel.add(extractButton);
        panel.add(cancelButton);
        return panel;
    }

    private JTabbedPane buildTabs() {
        textPane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        textPane.setText("Open an annotated XML section, or paste any biomedical text here and press Extract.");
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        evaluationArea.setEditable(false);
        evaluationArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JSplitPane textSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(textPane), new JScrollPane(logArea));
        textSplit.setResizeWeight(0.78);
        tabs.addTab("Text", textSplit);
        tabs.addTab("Entities", new JScrollPane(new JTable(entityModel)));
        tabs.addTab("Triples", new JScrollPane(new JTable(tripleModel)));
        tabs.addTab("Knowledge graph", graphPanel);
        tabs.addTab("Evaluation", new JScrollPane(evaluationArea));
        return tabs;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(220, 16));
        panel.add(status, BorderLayout.CENTER);
        panel.add(progress, BorderLayout.EAST);
        return panel;
    }

    private static DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void chooseFile() {
        File start = new File(Corpus.DEFAULT_ROOT).isDirectory() ? new File(Corpus.DEFAULT_ROOT) : new File(".");
        JFileChooser chooser = new JFileChooser(start);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            loadedDocument = new Xml(chooser.getSelectedFile());
            textPane.setText(loadedDocument.getText());
            textPane.setCaretPosition(0);
            log("Loaded " + loadedDocument);
            status.setText(loadedDocument.getName() + ", " + loadedDocument.getEntities().size() + " gold entities, " + loadedDocument.getLinks().size() + " gold links");
        } catch (Exception e) {
            loadedDocument = null;
            JOptionPane.showMessageDialog(this, "Cannot read the file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runExtraction() {
        String text = textPane.getText();
        if (text == null || text.isBlank()) {
            JOptionPane.showMessageDialog(this, "There is no text to process.", "Nothing to do", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Pipeline.Method method = (Pipeline.Method) methodBox.getSelectedItem();
        String name = loadedDocument != null ? loadedDocument.getName() : "pasted text";
        clearResults();
        busy(true, "Extracting with " + method.getLabel() + "...");
        start("extraction", () -> extract(method, name, text));
    }

    /**
     * Empties every result view before a new run, so that no table can be left showing the previous document while
     * another one is on screen.
     */
    private void clearResults() {
        entityModel.setRowCount(0);
        tripleModel.setRowCount(0);
        evaluationArea.setText("");
    }

    private void extract(Pipeline.Method method, String name, String text) {
        try {
            Pipeline pipeline = pipeline(method);
            long started = System.currentTimeMillis();
            Pipeline.Result result = pipeline.run(name, text);
            long elapsed = System.currentTimeMillis() - started;
            KnowledgeGraph graph = new KnowledgeGraph();
            graph.addAll(result.getTriples());
            SwingUtilities.invokeLater(() -> finish(() -> showResult(method, result, graph, text, elapsed), "Extraction finished."));
        } catch (Exception e) {
            fail(e);
        }
    }

    private void showResult(Pipeline.Method method, Pipeline.Result result, KnowledgeGraph graph, String text, long elapsed) {
        highlight(text, result.getEntities());
        fillEntities(result.getEntities());
        fillTriples(result.getTriples());
        graphPanel.display(graph, GraphPanel.DEFAULT_MAX_NODES);
        log(method.getLabel() + ": " + result.getEntities().size() + " entities, " + result.getTriples().size() + " triples in " + elapsed + " ms");
        if (loadedDocument != null && text.equals(loadedDocument.getText())) {
            showEvaluation(loadedDocument, result);
        } else if (loadedDocument != null) {
            evaluationArea.setText("The text was edited after " + loadedDocument.getName() + " was opened, so its gold spans no longer line up with it. Reopen the file to score the extraction.");
        } else {
            evaluationArea.setText("Scoring needs the gold annotations. Open a section of the corpus with Open XML and press Extract to see precision, recall and F1.");
        }
        busy(false, result.getEntities().size() + " entities, " + result.getTriples().size() + " triples, " + graph.nodeCount() + " graph nodes.");
    }

    private Pipeline pipeline(Pipeline.Method method) throws Exception {
        Pipeline pipeline = pipelines.get(method);
        if (pipeline == null) {
            pipeline = Pipeline.create(method, message -> SwingUtilities.invokeLater(() -> report(message)));
            pipelines.put(method, pipeline);
        }
        return pipeline;
    }

    private void report(String message) {
        status.setText(message);
        log(message);
    }

    private void highlight(String text, List<Entity> entities) {
        textPane.setText(text);
        StyledDocument document = textPane.getStyledDocument();
        SimpleAttributeSet gene = new SimpleAttributeSet();
        SimpleAttributeSet disease = new SimpleAttributeSet();
        StyleConstants.setBackground(gene, GENE_COLOUR);
        StyleConstants.setBackground(disease, DISEASE_COLOUR);
        for (Entity entity : entities) {
            if (entity.getEnd() <= document.getLength()) {
                document.setCharacterAttributes(entity.getStart(), entity.length(), entity.getType() == GENE ? gene : disease, false);
            }
        }
        textPane.setCaretPosition(0);
    }

    private void fillEntities(List<Entity> entities) {
        entityModel.setRowCount(0);
        for (Entity entity : entities) {
            entityModel.addRow(new Object[]{entity.getText(), entity.getType(), entity.getStart(), entity.getEnd(), String.format(Locale.ROOT, "%.3f", entity.getConfidence()), entity.getSource()});
        }
    }

    private void fillTriples(List<Triple> triples) {
        tripleModel.setRowCount(0);
        for (Triple triple : triples) {
            tripleModel.addRow(new Object[]{triple.getSubject().getText(), triple.getPredicate(), triple.getObject().getText(), String.format(Locale.ROOT, "%.3f", triple.getConfidence()), triple.getTrigger(), triple.getDocument()});
        }
    }

    private void showEvaluation(Xml document, Pipeline.Result result) {
        SpanMatcher.MatchResult strict = SpanMatcher.match(document.getEntities(), result.getEntities(), SpanMatcher.Mode.STRICT);
        SpanMatcher.MatchResult overlap = SpanMatcher.match(document.getEntities(), result.getEntities(), SpanMatcher.Mode.OVERLAP);
        List<Triple> gold = GoldTriples.of(document);
        StringBuilder report = new StringBuilder();
        report.append("Scored against the gold annotations of ").append(document.getName()).append("\n\n");
        report.append("Entities, exact span : ").append(strict.getScore()).append('\n');
        report.append("Entities, overlap    : ").append(overlap.getScore()).append('\n');
        for (org.example.Type type : new org.example.Type[]{GENE, DISEASE}) {
            report.append(String.format("  %-18s : %s%n", type, SpanMatcher.match(document.getEntities(), result.getEntities(), SpanMatcher.Mode.STRICT, type).getScore()));
        }
        report.append("Triples, pair only   : ").append(TripleMatcher.match(gold, result.getTriples(), TripleMatcher.Mode.PAIR).getScore()).append('\n');
        report.append("Triples, with label  : ").append(TripleMatcher.match(gold, result.getTriples(), TripleMatcher.Mode.LABELLED).getScore()).append("\n\n");
        appendAll(report, "Missed (false negatives), first 25:", strict.getMissed());
        appendAll(report, "Spurious (false positives), first 25:", strict.getSpurious());
        evaluationArea.setText(report.toString());
        evaluationArea.setCaretPosition(0);
    }

    private static void appendAll(StringBuilder report, String title, List<Entity> entities) {
        report.append(title).append('\n');
        for (int i = 0; i < Math.min(25, entities.size()); i++) {
            report.append("  ").append(entities.get(i)).append('\n');
        }
        report.append('\n');
    }

    /**
     * Switches the window between idle and working. Every control that starts a job is disabled while one runs,
     * including the file chooser, whose status message used to overwrite the message of the running job and leave the
     * window looking dead rather than busy.
     * @param running True while a background job is running.
     * @param message Message for the status bar.
     */
    private void busy(boolean running, String message) {
        this.running = running;
        extractButton.setEnabled(!running);
        loadButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        progress.setVisible(running);
        progress.setIndeterminate(running);
        status.setText(message);
    }

    /**
     * Runs a result handler and returns the window to idle whatever the handler does. Without the finally the window
     * stayed disabled forever when a handler threw, which looked exactly like the application having died.
     * @param handler Work to run on the event dispatch thread.
     * @param idleMessage Status message to fall back on when the handler fails.
     */
    private void finish(Runnable handler, String idleMessage) {
        try {
            handler.run();
        } catch (RuntimeException e) {
            e.printStackTrace();
            log("ERROR while showing the result: " + e);
            // Only a window the user is actually looking at gets a dialog, so a headless or scripted run reports the
            // failure through the log instead of blocking on a modal nobody asked for.
            if (isShowing()) {
                JOptionPane.showMessageDialog(this, String.valueOf(e), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } finally {
            if (running) {
                busy(false, idleMessage);
            }
        }
    }

    /**
     * Asks the running job to stop. The pipeline checks the interrupt between sections, so a corpus run gives up at
     * the next section rather than at the next document set.
     */
    private void cancel() {
        Thread worker = this.worker;
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
            log("Cancelling " + worker.getName() + "...");
            status.setText("Cancelling...");
        }
    }

    /**
     * Starts a background job, keeping a handle on it so that it can be cancelled.
     * @param name Name of the thread, shown when cancelling.
     * @param job Work to run off the event dispatch thread.
     */
    private void start(String name, Runnable job) {
        worker = new Thread(job, name);
        worker.start();
    }

    private void fail(Exception e) {
        if (e instanceof InterruptedException) {
            SwingUtilities.invokeLater(() -> busy(false, "Cancelled."));
            return;
        }
        e.printStackTrace();
        SwingUtilities.invokeLater(() -> {
            log("ERROR: " + e);
            busy(false, "Failed: " + e.getMessage());
            JOptionPane.showMessageDialog(this, String.valueOf(e.getMessage()), "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void shutdown() {
        graphPanel.stop();
        for (Pipeline pipeline : pipelines.values()) {
            pipeline.close();
        }
    }

    /**
     * Opens the window.
     * @param args Ignored.
     */
    public static void main(String[] args) {
        System.setProperty("org.graphstream.ui", "swing");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            System.out.println("Falling back to the cross platform look and feel.");
        }
        SwingUtilities.invokeLater(() -> {
            BioTriplExFrame frame = new BioTriplExFrame();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
