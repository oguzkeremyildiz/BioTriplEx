package org.example;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;

import ai.djl.huggingface.translator.TokenClassificationTranslatorFactory;
import ai.djl.modality.nlp.translator.NamedEntity;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.inference.Predictor;
import ai.djl.training.util.ProgressBar;

public class NERFrontend extends JFrame {

    private final JComboBox<String> comboType;
    private final JComboBox<String> comboMethod;
    private final JTextArea textArea;
    private final JTextArea resultArea;
    private final JButton btnExtract;
    private File selectedFile = null;

    public NERFrontend() {
        setTitle("BioNER & Knowledge Graph Extractor");
        setSize(900, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        comboType = new JComboBox<>(new String[]{"Disease (DOID)", "Gene (HG_PCO)"});
        comboMethod = new JComboBox<>(new String[]{"Exact Match (KMP)", "BioNER (Deep Learning)"});

        JButton btnLoadFile = new JButton("Select File");
        btnExtract = new JButton("Extract & Score");

        btnExtract.setBackground(new Color(60, 130, 200));
        btnExtract.setForeground(Color.BLACK);
        btnExtract.setOpaque(true);

        topPanel.add(new JLabel("Entity Type:"));
        topPanel.add(comboType);
        topPanel.add(new JLabel("Method:"));
        topPanel.add(comboMethod);
        topPanel.add(new JLabel("|"));
        topPanel.add(btnLoadFile);
        topPanel.add(btnExtract);

        add(topPanel, BorderLayout.NORTH);

        textArea = new JTextArea();
        textArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        resultArea = new JTextArea(15, 50);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        resultArea.setBackground(new Color(245, 245, 245));
        add(new JScrollPane(resultArea), BorderLayout.SOUTH);

        btnLoadFile.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(".");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                selectedFile = fc.getSelectedFile();
                textArea.setText("File Loaded: " + selectedFile.getAbsolutePath());
            }
        });

        btnExtract.addActionListener(e -> processExtraction());
    }

    private void processExtraction() {
        new Thread(() -> {
            try {
                btnExtract.setEnabled(false);
                resultArea.setText("Initializing process...\n");

                boolean isDisease = comboType.getSelectedIndex() == 0;
                org.example.Type selectedType = isDisease ? org.example.Type.DISEASE : org.example.Type.GENE;
                boolean useBioNER = comboMethod.getSelectedIndex() == 1;

                String textToProcess = textArea.getText();
                HashMap<String, ArrayList<Integer>> predictedMap = new HashMap<>();
                ArrayList<AbstractMap.SimpleEntry<String, Integer>> actualTags = new ArrayList<>();

                if (selectedFile != null && selectedFile.getName().toLowerCase().endsWith(".xml")) {
                    Xml xml = new Xml(selectedFile);
                    textToProcess = xml.getText();
                    actualTags = xml.getTags(selectedType);
                }

                if (textToProcess == null || textToProcess.trim().isEmpty()) {
                    resultArea.setText("ERROR: No text found to process!");
                    btnExtract.setEnabled(true);
                    return;
                }

                if (useBioNER) {
                    resultArea.append("> Loading BioBERT Model...\n");
                    predictedMap = runBioNERModel(textToProcess, selectedType);
                } else {
                    resultArea.append("> Performing Exact Match...\n");
                    predictedMap = runExactMatch(textToProcess, selectedType);
                }

                final HashMap<String, ArrayList<Integer>> finalPredicted = predictedMap;
                final ArrayList<AbstractMap.SimpleEntry<String, Integer>> finalActual = actualTags;

                SwingUtilities.invokeLater(() -> displayResults(finalActual, finalPredicted));

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> resultArea.append("\nSYSTEM ERROR: " + ex.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> btnExtract.setEnabled(true));
            }
        }).start();
    }

    private HashMap<String, ArrayList<Integer>> runExactMatch(String text, org.example.Type type) throws Exception {
        String path = (type == org.example.Type.DISEASE) ? "ontologies&others/doid.obo" : "ontologies&others/HG_PCO.obo";
        Ontology ontology = new Ontology(new File(path), type);
        HashMap<String, ArrayList<Integer>> map = new HashMap<>();
        for (int i = 0; i < ontology.size(); i++) {
            map.putAll(ontology.getTerm(i).findMatches(text));
        }
        return map;
    }

    private HashMap<String, ArrayList<Integer>> runBioNERModel(String text, org.example.Type type) throws Exception {
        String modelName = (type == org.example.Type.DISEASE)
                ? "alvaroalon2/biobert_diseases_ner"
                : "monologg/biobert_v1.1_pubmed_ner";

        TokenClassificationTranslatorFactory factory = new TokenClassificationTranslatorFactory();

        Criteria<String, NamedEntity[]> criteria = Criteria.builder()
                .setTypes(String.class, NamedEntity[].class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName)
                .optEngine("PyTorch")
                .optTranslatorFactory(factory)
                .optArgument("includeTokenTypes", "false")
                .optProgress(new ProgressBar())
                .build();

        HashMap<String, ArrayList<Integer>> results = new HashMap<>();

        try (ZooModel<String, NamedEntity[]> model = criteria.loadModel();
             Predictor<String, NamedEntity[]> predictor = model.newPredictor()) {

            NamedEntity[] entities = predictor.predict(text);

            for (NamedEntity entity : entities) {
                int start = (int) entity.getStart();
                int end = (int) entity.getEnd();

                if (start >= 0 && end <= text.length() && start < end) {
                    String word = text.substring(start, end);
                    results.computeIfAbsent(word, k -> new ArrayList<>()).add(start);
                }
            }
        }
        return results;
    }

    private void displayResults(ArrayList<AbstractMap.SimpleEntry<String, Integer>> actual, HashMap<String, ArrayList<Integer>> predicted) {
        StringBuilder sb = new StringBuilder("=== ANALYSIS COMPLETED ===\n\n");

        if (!actual.isEmpty()) {
            Score score = new Score(actual, predicted);
            sb.append("PERFORMANCE METRICS:\n");
            sb.append(String.format("- Precision : %.4f\n", score.getPrecision()));
            sb.append(String.format("- Recall    : %.4f\n", score.getRecall()));
            sb.append(String.format("- F1 Score  : %.4f\n", score.getF1()));
            sb.append("--------------------------------------------------\n\n");
        }

        sb.append("EXTRACTED ENTITIES:\n");
        predicted.forEach((k, v) -> {
            if(!v.isEmpty()) sb.append(String.format("• %s -> Locations: %s\n", k, v));
        });

        resultArea.setText(sb.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            NERFrontend frame = new NERFrontend();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}