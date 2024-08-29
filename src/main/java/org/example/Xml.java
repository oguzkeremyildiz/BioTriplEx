package org.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class Xml {

    private final HashMap<String, ArrayList<String>> xml;

    public Xml(File file) throws FileNotFoundException {
        Scanner scanner = new Scanner(file);
        xml = new HashMap<>();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.contains("<TEXT>")) {
                xml.put("TEXT", new ArrayList<>());
                xml.get("TEXT").add("");
                line = scanner.nextLine();
                do {
                    xml.get("TEXT").set(0, xml.get("TEXT").get(0) + line + " ");
                    line = scanner.nextLine();
                } while (!line.contains("</TEXT>"));
            } else if (line.contains("<TAGS>")) {
                line = scanner.nextLine();
                while (!line.contains("</TAGS>")) {
                    String key = line.substring(line.indexOf("<") + 1, line.indexOf(" id="));
                    if (!xml.containsKey(key)) {
                        xml.put(key, new ArrayList<>());
                    }
                    xml.get(key).add(line.substring(line.indexOf("id"), line.indexOf(" />")));
                    line = scanner.nextLine();
                }
            }
        }
        scanner.close();
    }

    public String getText() {
        return xml.get("TEXT").get(0);
    }

    private ArrayList<String> split(String line) {
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') {
                i++;
                StringBuilder cur = new StringBuilder();
                while (i < line.length() && line.charAt(i) != '"') {
                    cur.append(line.charAt(i));
                    i++;
                }
                result.add(cur.toString());
            }
        }
        return result;
    }

    public ArrayList<AbstractMap.SimpleEntry<String, Integer>> getTags(Type type) {
        ArrayList<AbstractMap.SimpleEntry<String, Integer>> tags = new ArrayList<>();
        if (xml.containsKey(type.toString())) {
            ArrayList<String> list = xml.get(type.toString());
            for (String s : list) {
                ArrayList<String> strings = split(s);
                String spans = strings.get(1).trim();
                String text = strings.get(2).trim();
                tags.add(new AbstractMap.SimpleEntry<>(text, Integer.parseInt(spans.substring(0, spans.indexOf("~")))));
            }
        }
        return tags;
    }
}
