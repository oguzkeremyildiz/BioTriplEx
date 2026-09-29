package org.example.ner;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.tokenizers.jni.CharSpan;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslateException;
import ai.djl.util.Pair;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.Type;
import org.example.text.Sentences;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * BioNER, a BioBERT token classifier executed with ONNX Runtime through DJL.
 * The document is packed into windows of at most 512 tokens along sentence boundaries, every window is a verbatim
 * slice of the text, and the character offsets the tokenizer reports are shifted back by the window start. Predicted
 * spans therefore land on the coordinate system the gold spans use and can be scored without an alignment step.
 * Sub-word pieces are folded into words by word id and a word takes the label of its first piece, which is the
 * aggregation_strategy="first" rule of the HuggingFace pipeline.
 */
public final class BioNerExtractor implements EntityExtractor {

    private static final int MAX_TOKENS = 512;
    private static final int WINDOW_BUDGET = MAX_TOKENS - 12;

    private final BioNerModel descriptor;
    private final ZooModel<NDList, NDList> model;
    private final Predictor<NDList, NDList> predictor;
    private final HuggingFaceTokenizer tokenizer;
    private final String[] labels;
    private final ArrayList<String> inputNames;

    private BioNerExtractor(BioNerModel descriptor, ZooModel<NDList, NDList> model, HuggingFaceTokenizer tokenizer, String[] labels) {
        this.descriptor = descriptor;
        this.model = model;
        this.predictor = model.newPredictor();
        this.tokenizer = tokenizer;
        this.labels = labels;
        this.inputNames = new ArrayList<>();
        for (Pair<String, Shape> input : model.describeInput()) {
            inputNames.add(input.getKey());
        }
    }

    /**
     * Loads the model that tags the given type, downloading it on first use.
     * @param type Entity type to tag.
     * @param progress Receives progress lines, may be null.
     * @return The loaded extractor.
     * @throws IOException When the model cannot be downloaded or loaded.
     */
    public static BioNerExtractor load(Type type, Consumer<String> progress) throws IOException {
        BioNerModel descriptor = BioNerModel.forType(type);
        Path directory = descriptor.download(progress);
        if (progress != null) {
            progress.accept("Loading " + descriptor.getRepository());
        }
        Criteria<NDList, NDList> criteria = Criteria.builder().setTypes(NDList.class, NDList.class).optModelPath(directory).optModelName("model").optEngine("OnnxRuntime").optTranslator(new NoopTranslator(null)).build();
        try {
            ZooModel<NDList, NDList> model = criteria.loadModel();
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder().optTokenizerPath(directory).optAddSpecialTokens(true).optTruncation(true).optMaxLength(MAX_TOKENS).build();
            return new BioNerExtractor(descriptor, model, tokenizer, readLabels(directory));
        } catch (ModelNotFoundException | MalformedModelException e) {
            throw new IOException("Cannot load " + descriptor.getRepository() + " from " + directory + ", delete the directory to force a fresh download.", e);
        }
    }

    private static String[] readLabels(Path directory) throws IOException {
        Path config = directory.resolve("config.json");
        try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement id2label = root.get("id2label");
            if (id2label == null || !id2label.isJsonObject()) {
                throw new IOException("config.json has no id2label map: " + config);
            }
            TreeMap<Integer, String> ordered = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : id2label.getAsJsonObject().entrySet()) {
                ordered.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
            }
            return ordered.values().toArray(new String[0]);
        }
    }

    @Override
    public List<Entity> extract(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        ArrayList<Entity> found = new ArrayList<>();
        for (int[] window : windows(text)) {
            found.addAll(extractWindow(text, window[0], window[1]));
        }
        Collections.sort(found);
        return found;
    }

    private List<Entity> extractWindow(String document, int windowStart, int windowEnd) {
        Encoding encoding = tokenizer.encode(document.substring(windowStart, windowEnd));
        long[] ids = encoding.getIds();
        if (ids.length == 0) {
            return Collections.emptyList();
        }
        float[] logits = forward(encoding);
        CharSpan[] spans = encoding.getCharTokenSpans();
        long[] wordIds = encoding.getWordIds();
        int labelCount = labels.length;
        ArrayList<Entity> found = new ArrayList<>();
        String openLabel = null;
        int openStart = -1;
        int openEnd = -1;
        double openScore = 0;
        int openWords = 0;
        int i = 0;
        while (i < ids.length) {
            if (i >= spans.length || spans[i] == null || wordIds[i] < 0) {
                i++;
                continue;
            }
            long word = wordIds[i];
            int first = i;
            int wordStart = spans[i].getStart();
            int wordEnd = spans[i].getEnd();
            while (i + 1 < ids.length && wordIds[i + 1] == word && spans[i + 1] != null) {
                i++;
                wordEnd = Math.max(wordEnd, spans[i].getEnd());
            }
            i++;
            int best = 0;
            for (int label = 1; label < labelCount; label++) {
                if (logits[first * labelCount + label] > logits[first * labelCount + best]) {
                    best = label;
                }
            }
            double score = softmax(logits, first * labelCount, labelCount, best);
            String label = labels[best];
            String tag = label.startsWith("B-") || label.startsWith("I-") ? label.substring(2) : null;
            if (tag != null && label.startsWith("I-") && tag.equals(openLabel)) {
                openEnd = wordEnd;
                openScore += score;
                openWords++;
                continue;
            }
            if (openLabel != null) {
                addEntity(found, document, windowStart, openLabel, openStart, openEnd, openScore / openWords);
                openLabel = null;
            }
            if (tag != null) {
                openLabel = tag;
                openStart = wordStart;
                openEnd = wordEnd;
                openScore = score;
                openWords = 1;
            }
        }
        if (openLabel != null) {
            addEntity(found, document, windowStart, openLabel, openStart, openEnd, openScore / openWords);
        }
        return found;
    }

    private void addEntity(List<Entity> found, String document, int windowStart, String label, int start, int end, double score) {
        Entity entity = Entity.trimmed(typeOf(label), document, windowStart + start, windowStart + end, score, name());
        if (entity != null) {
            found.add(entity);
        }
    }

    private Type typeOf(String label) {
        String upper = label.toUpperCase(Locale.ROOT);
        if (upper.contains("GENE") || upper.contains("PROTEIN") || upper.contains("DNA") || upper.contains("RNA")) {
            return Type.GENE;
        }
        if (upper.contains("DISEASE") || upper.contains("DISORDER") || upper.contains("SYNDROME")) {
            return Type.DISEASE;
        }
        return descriptor.getType();
    }

    /**
     * Runs the network over one encoded window.
     * @param encoding Tokenized window.
     * @return The logits of the sequence, flattened as token by label.
     */
    private float[] forward(Encoding encoding) {
        try (NDManager manager = model.getNDManager().newSubManager()) {
            NDList inputs = new NDList(inputNames.size());
            for (String inputName : inputNames) {
                long[] values = valuesOf(encoding, inputName);
                NDArray array = manager.create(values, new Shape(1, values.length));
                array.setName(inputName);
                inputs.add(array);
            }
            return predictor.predict(inputs).get(0).toFloatArray();
        } catch (TranslateException e) {
            throw new IllegalStateException("BioNER inference failed", e);
        }
    }

    private static long[] valuesOf(Encoding encoding, String inputName) {
        switch (inputName) {
            case "input_ids":
                return encoding.getIds();
            case "attention_mask":
                return encoding.getAttentionMask();
            case "token_type_ids":
                return encoding.getTypeIds();
            default:
                throw new IllegalStateException("Model expects an unsupported input: " + inputName);
        }
    }

    private static double softmax(float[] logits, int offset, int count, int index) {
        double max = logits[offset];
        for (int i = 1; i < count; i++) {
            max = Math.max(max, logits[offset + i]);
        }
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += Math.exp(logits[offset + i] - max);
        }
        return Math.exp(logits[offset + index] - max) / sum;
    }

    /**
     * Packs the document into windows of at most WINDOW_BUDGET tokens, breaking on sentence boundaries.
     * @param text Document to split.
     * @return The start and end offset of every window.
     */
    private List<int[]> windows(String text) {
        ArrayList<int[]> windows = new ArrayList<>();
        int windowStart = -1;
        int windowEnd = -1;
        int budget = 0;
        for (int[] sentence : Sentences.split(text)) {
            for (int[] piece : splitLongSentence(text, sentence)) {
                int tokens = countTokens(text.substring(piece[0], piece[1]));
                if (windowStart >= 0 && budget + tokens > WINDOW_BUDGET) {
                    windows.add(new int[]{windowStart, windowEnd});
                    windowStart = -1;
                }
                if (windowStart < 0) {
                    windowStart = piece[0];
                    budget = 0;
                }
                windowEnd = piece[1];
                budget += tokens;
            }
        }
        if (windowStart >= 0) {
            windows.add(new int[]{windowStart, windowEnd});
        }
        return windows;
    }

    private List<int[]> splitLongSentence(String text, int[] sentence) {
        if (countTokens(text.substring(sentence[0], sentence[1])) <= WINDOW_BUDGET) {
            return Collections.singletonList(sentence);
        }
        ArrayList<int[]> pieces = new ArrayList<>();
        int start = sentence[0];
        int lastBreak = -1;
        for (int i = sentence[0]; i < sentence[1]; i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                lastBreak = i;
            }
            if (i - start > WINDOW_BUDGET * 3 && lastBreak > start) {
                pieces.add(new int[]{start, lastBreak});
                start = lastBreak + 1;
            }
        }
        if (start < sentence[1]) {
            pieces.add(new int[]{start, sentence[1]});
        }
        return pieces;
    }

    private int countTokens(String value) {
        return tokenizer.encode(value, false, false).getIds().length;
    }

    public BioNerModel getDescriptor() {
        return descriptor;
    }

    @Override
    public String name() {
        return "BioNER";
    }

    @Override
    public void close() {
        predictor.close();
        model.close();
        tokenizer.close();
    }
}
