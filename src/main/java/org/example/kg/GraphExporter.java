package org.example.kg;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Writes a knowledge graph in the formats the usual biomedical tooling reads: GraphML for Cytoscape, Gephi and yEd, a
 * node and edge CSV pair for spreadsheets, JSON for web front ends, and Cypher statements for loading it into Neo4j.
 */
public final class GraphExporter {

    private GraphExporter() {
    }

    /**
     * Writes every supported format into a directory.
     * @param graph Graph to export.
     * @param directory Directory to write into, created when missing.
     * @param prefix Base name of the written files.
     * @throws IOException When a file cannot be written.
     */
    public static void writeAll(KnowledgeGraph graph, Path directory, String prefix) throws IOException {
        Files.createDirectories(directory);
        writeGraphMl(graph, directory.resolve(prefix + ".graphml"));
        writeNodesCsv(graph, directory.resolve(prefix + "-nodes.csv"));
        writeEdgesCsv(graph, directory.resolve(prefix + "-edges.csv"));
        writeJson(graph, directory.resolve(prefix + ".json"));
        writeCypher(graph, directory.resolve(prefix + ".cypher"));
    }

    /**
     * Writes the graph as GraphML.
     * @param graph Graph to export.
     * @param file File to write.
     * @throws IOException When the file cannot be written.
     */
    public static void writeGraphMl(KnowledgeGraph graph, Path file) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            out.write("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n");
            out.write("  <key id=\"label\" for=\"node\" attr.name=\"label\" attr.type=\"string\"/>\n");
            out.write("  <key id=\"type\" for=\"node\" attr.name=\"type\" attr.type=\"string\"/>\n");
            out.write("  <key id=\"mentions\" for=\"node\" attr.name=\"mentions\" attr.type=\"int\"/>\n");
            out.write("  <key id=\"predicate\" for=\"edge\" attr.name=\"predicate\" attr.type=\"string\"/>\n");
            out.write("  <key id=\"support\" for=\"edge\" attr.name=\"support\" attr.type=\"int\"/>\n");
            out.write("  <key id=\"documents\" for=\"edge\" attr.name=\"documents\" attr.type=\"int\"/>\n");
            out.write("  <graph id=\"BioTriplEx\" edgedefault=\"directed\">\n");
            for (KnowledgeGraph.Node node : graph.getNodes()) {
                out.write("    <node id=\"" + xml(node.getId()) + "\">\n");
                out.write("      <data key=\"label\">" + xml(node.getLabel()) + "</data>\n");
                out.write("      <data key=\"type\">" + node.getType() + "</data>\n");
                out.write("      <data key=\"mentions\">" + node.getMentions() + "</data>\n");
                out.write("    </node>\n");
            }
            int index = 0;
            for (KnowledgeGraph.Edge edge : graph.getEdges()) {
                out.write("    <edge id=\"e" + index + "\" source=\"" + xml(edge.getSource().getId()) + "\" target=\"" + xml(edge.getTarget().getId()) + "\">\n");
                out.write("      <data key=\"predicate\">" + xml(edge.getPredicate()) + "</data>\n");
                out.write("      <data key=\"support\">" + edge.getSupport() + "</data>\n");
                out.write("      <data key=\"documents\">" + edge.getDocumentCount() + "</data>\n");
                out.write("    </edge>\n");
                index++;
            }
            out.write("  </graph>\n");
            out.write("</graphml>\n");
        }
    }

    /**
     * Writes the nodes as CSV.
     * @param graph Graph to export.
     * @param file File to write.
     * @throws IOException When the file cannot be written.
     */
    public static void writeNodesCsv(KnowledgeGraph graph, Path file) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("id,label,type,mentions\n");
            for (KnowledgeGraph.Node node : graph.getNodes()) {
                out.write(csv(node.getId()) + ',' + csv(node.getLabel()) + ',' + node.getType() + ',' + node.getMentions() + '\n');
            }
        }
    }

    /**
     * Writes the edges as CSV, with the first supporting sentence of every edge.
     * @param graph Graph to export.
     * @param file File to write.
     * @throws IOException When the file cannot be written.
     */
    public static void writeEdgesCsv(KnowledgeGraph graph, Path file) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("source,target,predicate,support,documents,confidence,evidence\n");
            for (KnowledgeGraph.Edge edge : graph.getEdges()) {
                Triple first = edge.getEvidence().get(0);
                out.write(csv(edge.getSource().getLabel()) + ',' + csv(edge.getTarget().getLabel()) + ',' + csv(edge.getPredicate()));
                out.write("," + edge.getSupport() + ',' + edge.getDocumentCount());
                out.write("," + String.format(Locale.ROOT, "%.3f", edge.getConfidence()) + ',' + csv(first.getEvidence()) + '\n');
            }
        }
    }

    /**
     * Writes the graph as JSON.
     * @param graph Graph to export.
     * @param file File to write.
     * @throws IOException When the file cannot be written.
     */
    public static void writeJson(KnowledgeGraph graph, Path file) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("{\n  \"nodes\": [\n");
            boolean first = true;
            for (KnowledgeGraph.Node node : graph.getNodes()) {
                if (!first) {
                    out.write(",\n");
                }
                first = false;
                out.write("    {\"id\": " + json(node.getId()) + ", \"label\": " + json(node.getLabel()) + ", \"type\": " + json(node.getType().toString()) + ", \"mentions\": " + node.getMentions() + "}");
            }
            out.write("\n  ],\n  \"edges\": [\n");
            first = true;
            for (KnowledgeGraph.Edge edge : graph.getEdges()) {
                if (!first) {
                    out.write(",\n");
                }
                first = false;
                Triple evidence = edge.getEvidence().get(0);
                out.write("    {\"source\": " + json(edge.getSource().getId()));
                out.write(", \"target\": " + json(edge.getTarget().getId()));
                out.write(", \"predicate\": " + json(edge.getPredicate()));
                out.write(", \"support\": " + edge.getSupport());
                out.write(", \"documents\": " + edge.getDocumentCount());
                out.write(", \"confidence\": " + String.format(Locale.ROOT, "%.3f", edge.getConfidence()));
                out.write(", \"evidence\": " + json(evidence.getEvidence()));
                out.write(", \"document\": " + json(evidence.getDocument()) + "}");
            }
            out.write("\n  ]\n}\n");
        }
    }

    /**
     * Writes a Neo4j import script, one MERGE per node and per relation.
     * @param graph Graph to export.
     * @param file File to write.
     * @throws IOException When the file cannot be written.
     */
    public static void writeCypher(KnowledgeGraph graph, Path file) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("// BioTriplEx knowledge graph\n");
            for (KnowledgeGraph.Node node : graph.getNodes()) {
                String name = node.getType().toString();
                String label = name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
                out.write("MERGE (:" + label + " {id: " + json(node.getId()) + ", name: " + json(node.getLabel()) + ", mentions: " + node.getMentions() + "});\n");
            }
            for (KnowledgeGraph.Edge edge : graph.getEdges()) {
                out.write("MATCH (a {id: " + json(edge.getSource().getId()) + "}), ");
                out.write("(b {id: " + json(edge.getTarget().getId()) + "}) ");
                out.write("MERGE (a)-[:RELATED {predicate: " + json(edge.getPredicate()) + ", support: " + edge.getSupport() + "}]->(b);\n");
            }
        }
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String csv(String value) {
        return '"' + value.replace("\n", " ").replace("\r", " ").replace("\"", "\"\"") + '"';
    }

    private static String json(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
