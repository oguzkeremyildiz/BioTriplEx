package org.example.ner;

import org.example.Type;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * The transformer checkpoints used for BioNER, and the download cache that backs them.
 * The CS 401 prototype pointed DJL at https://huggingface.co/model directly, which cannot work: the PyTorch engine
 * only loads TorchScript archives while a HuggingFace repository holds a pytorch_model.bin state dict, and the gene
 * model it named, monologg/biobert_v1.1_pubmed_ner, does not exist. These checkpoints are BioBERT derived token
 * classifiers exported to ONNX, so the ONNX Runtime engine can execute them straight from the hub with no Python step.
 */
public enum BioNerModel {

    DISEASE(Type.DISEASE, "OpenMed/OpenMed-NER-DiseaseDetect-PubMed-109M-v1-onnx-android"),
    GENE(Type.GENE, "OpenMed/OpenMed-NER-GenomeDetect-PubMed-109M-v1-onnx-android");

    private static final String CACHE_PROPERTY = "biotriplex.models.dir";
    private static final String PRECISION_PROPERTY = "biotriplex.model.precision";
    private static final String HUB = "https://huggingface.co/";
    private static final String READY_MARKER = ".ready";

    private final Type type;
    private final String repository;

    BioNerModel(Type type, String repository) {
        this.type = type;
        this.repository = repository;
    }

    /**
     * Returns the checkpoint that tags the given type.
     * @param type Entity type to tag.
     * @return The matching model.
     */
    public static BioNerModel forType(Type type) {
        return type == Type.GENE ? GENE : DISEASE;
    }

    public Type getType() {
        return type;
    }

    public String getRepository() {
        return repository;
    }

    /**
     * Returns the local directory the model is cached in, whether or not it has been downloaded yet.
     * @return The cache directory of this model.
     */
    public Path getDirectory() {
        return Paths.get(System.getProperty(CACHE_PROPERTY, "models"), repository.replace('/', '_'));
    }

    public boolean isDownloaded() {
        return Files.exists(getDirectory().resolve(READY_MARKER));
    }

    /**
     * Returns a rough download size, for telling the user what they are waiting for.
     * @return The size of the weights as text.
     */
    public String getDownloadSize() {
        return fullPrecision() ? "about 440 MB" : "about 180 MB";
    }

    private static boolean fullPrecision() {
        return "fp32".equalsIgnoreCase(System.getProperty(PRECISION_PROPERTY, "int8"));
    }

    /**
     * Returns the cache directory, downloading model.onnx, tokenizer.json and config.json on first use.
     * @param progress Receives progress lines, may be null.
     * @return The directory holding the model.
     * @throws IOException When the download fails.
     */
    public Path download(Consumer<String> progress) throws IOException {
        Path directory = getDirectory();
        if (isDownloaded()) {
            return directory;
        }
        Files.createDirectories(directory);
        report(progress, "Downloading " + repository + " (" + getDownloadSize() + ", one time only)");
        String weights = fullPrecision() ? "model.onnx" : "model_int8.onnx";
        fetch(weights, directory.resolve("model.onnx"), progress);
        fetch("tokenizer.json", directory.resolve("tokenizer.json"), progress);
        fetch("config.json", directory.resolve("config.json"), progress);
        Files.writeString(directory.resolve(READY_MARKER), repository + "\n" + weights + "\n");
        report(progress, "Model ready in " + directory);
        return directory;
    }

    private void fetch(String remoteName, Path target, Consumer<String> progress) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        URI uri = URI.create(HUB + repository + "/resolve/main/" + remoteName + "?download=true");
        report(progress, "  " + remoteName);
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(30)).build();
        try {
            HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Download failed with HTTP " + response.statusCode() + ": " + uri);
            }
            try (InputStream in = response.body()) {
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + uri, e);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }
}
