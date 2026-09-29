package org.example;

import org.example.kg.EntityLink;
import org.example.kg.RelationMention;
import org.example.ner.Entity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * One annotated paper section of the BioTriplEx corpus.
 * The file holds the section text in a CDATA block and the gold annotations in a TAGS block. The spans="start~end"
 * attributes index into the CDATA content including its leading newline, so the text is kept verbatim here: any
 * normalization would shift every offset and silently break scoring. The previous implementation rebuilt the text line
 * by line and dropped that newline, which is why the KMP matcher had to add one to every offset it reported.
 */
public class Xml {

    private static final String TEXT_OPEN = "<TEXT><![CDATA[";
    private static final String TEXT_CLOSE = "]]></TEXT>";

    private final String name;
    private final String text;
    private final ArrayList<Entity> entities;
    private final ArrayList<RelationMention> relations;
    private final ArrayList<EntityLink> links;
    private final LinkedHashMap<String, Entity> entitiesById;
    private final LinkedHashMap<String, RelationMention> relationsById;

    /**
     * Reads an annotated section from a file.
     * @param file File to read.
     * @throws IOException When the file cannot be read.
     */
    public Xml(File file) throws IOException {
        this(file.getName(), Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * Parses an annotated section.
     * @param name Name of the section, used as provenance.
     * @param content Content of the XML file.
     */
    public Xml(String name, String content) {
        this.name = name;
        this.text = extractText(content);
        this.entities = new ArrayList<>();
        this.relations = new ArrayList<>();
        this.links = new ArrayList<>();
        this.entitiesById = new LinkedHashMap<>();
        this.relationsById = new LinkedHashMap<>();
        parseTags(content);
        Collections.sort(entities);
    }

    /**
     * Reads a document, turning the checked exception into an unchecked one so that it can be used in a stream.
     * @param file File to read.
     * @return The parsed document.
     */
    public static Xml load(File file) {
        try {
            return new Xml(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static String extractText(String content) {
        int open = content.indexOf(TEXT_OPEN);
        int close = content.indexOf(TEXT_CLOSE);
        if (open >= 0 && close >= 0) {
            return content.substring(open + TEXT_OPEN.length(), close);
        }
        open = content.indexOf("<TEXT>");
        close = content.indexOf("</TEXT>");
        if (open < 0 || close < 0) {
            return content;
        }
        return content.substring(open + "<TEXT>".length(), close);
    }

    private void parseTags(String content) {
        int open = content.indexOf("<TAGS>");
        int close = content.indexOf("</TAGS>");
        if (open < 0 || close < 0) {
            return;
        }
        for (String raw : content.substring(open + "<TAGS>".length(), close).split("\n")) {
            String line = raw.trim();
            int nameEnd = line.indexOf(' ');
            if (line.isEmpty() || !line.startsWith("<") || nameEnd < 0) {
                continue;
            }
            Map<String, String> attributes = parseAttributes(line);
            switch (line.substring(1, nameEnd)) {
                case "GENE":
                    addEntity(Type.GENE, attributes);
                    break;
                case "DISEASE":
                    addEntity(Type.DISEASE, attributes);
                    break;
                case "RELATION":
                    addRelation(attributes);
                    break;
                case "ENTITY_LINKING":
                    links.add(new EntityLink(attributes.get("id"), attributes.get("geneID"), unescape(attributes.get("geneText")), attributes.get("diseaseID"), unescape(attributes.get("diseaseText")), attributes.get("relationID"), unescape(attributes.get("relationText"))));
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Adds a gold mention. A discontinuous annotation such as "prostate ... cancer" becomes one mention per range, so
     * that every gold span is a contiguous piece of text, like the extractors produce.
     * @param type Type of the mention.
     * @param attributes Attributes of the tag.
     */
    private void addEntity(Type type, Map<String, String> attributes) {
        String id = attributes.get("id");
        boolean first = true;
        for (int[] span : parseSpans(attributes.get("spans"))) {
            if (span[0] < 0 || span[1] > text.length() || span[0] >= span[1]) {
                continue;
            }
            Entity entity = new Entity(type, text.substring(span[0], span[1]), span[0], span[1]);
            entities.add(entity);
            if (first && id != null) {
                entitiesById.put(id, entity);
                first = false;
            }
        }
    }

    private void addRelation(Map<String, String> attributes) {
        int[][] spans = parseSpans(attributes.get("spans"));
        if (spans.length == 0) {
            return;
        }
        RelationMention relation = new RelationMention(attributes.get("id"), unescape(attributes.getOrDefault("text", "")), attributes.getOrDefault("relation", "related_to"), spans[0][0], spans[spans.length - 1][1]);
        relations.add(relation);
        if (relation.getId() != null) {
            relationsById.put(relation.getId(), relation);
        }
    }

    /**
     * Parses a spans attribute, both the plain "12~20" and the discontinuous "12~20,30~36" form.
     * @param value Value of the attribute.
     * @return The start and end offset of every range.
     */
    private static int[][] parseSpans(String value) {
        if (value == null || value.isEmpty()) {
            return new int[0][];
        }
        ArrayList<int[]> spans = new ArrayList<>();
        for (String part : value.split(",")) {
            int separator = part.indexOf('~');
            if (separator < 0) {
                continue;
            }
            try {
                spans.add(new int[]{Integer.parseInt(part.substring(0, separator).trim()), Integer.parseInt(part.substring(separator + 1).trim())});
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return spans.toArray(new int[0][]);
    }

    private static Map<String, String> parseAttributes(String line) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        int i = 0;
        while (i < line.length()) {
            int equals = line.indexOf('=', i);
            if (equals < 0 || equals + 1 >= line.length() || line.charAt(equals + 1) != '"') {
                break;
            }
            int keyStart = equals - 1;
            while (keyStart >= 0 && !Character.isWhitespace(line.charAt(keyStart))) {
                keyStart--;
            }
            int valueEnd = line.indexOf('"', equals + 2);
            if (valueEnd < 0) {
                break;
            }
            attributes.put(line.substring(keyStart + 1, equals), line.substring(equals + 2, valueEnd));
            i = valueEnd + 1;
        }
        return attributes;
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&amp;lt;", "<").replace("&amp;gt;", ">").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the verbatim section text, whose character 0 matches offset 0 of the gold spans.
     * @return The section text.
     */
    public String getText() {
        return text;
    }

    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }

    /**
     * Returns the gold mentions of one type.
     * @param type Type to select.
     * @return The matching mentions.
     */
    public List<Entity> getEntities(Type type) {
        ArrayList<Entity> selected = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity.getType() == type) {
                selected.add(entity);
            }
        }
        return selected;
    }

    public List<RelationMention> getRelations() {
        return Collections.unmodifiableList(relations);
    }

    /**
     * Returns the gold gene, relation and disease links, the ground truth for triple extraction.
     * @return The gold links.
     */
    public List<EntityLink> getLinks() {
        return Collections.unmodifiableList(links);
    }

    /**
     * Looks up a gold mention by the id its tag carries.
     * @param id Identifier of the tag.
     * @return The mention, or null.
     */
    public Entity getEntityById(String id) {
        return entitiesById.get(id);
    }

    /**
     * Looks up a gold relation trigger by the id its tag carries.
     * @param id Identifier of the tag.
     * @return The trigger, or null.
     */
    public RelationMention getRelationById(String id) {
        return relationsById.get(id);
    }

    public boolean isEmpty() {
        return text.isBlank();
    }

    @Override
    public String toString() {
        return name + " (" + text.length() + " chars, " + entities.size() + " gold entities, " + links.size() + " gold links)";
    }
}
