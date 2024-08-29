package org.example;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class Main {

    // It can be faster by using the Aho-Corasick algorithm instead of KMP.

    public static void main(String[] args) throws FileNotFoundException {
        Ontology ontology = new Ontology(new File("ontologies&others/doid.obo"), Type.DISEASE);
        String fileName = "Annotated Full Text Paper Folders-6";
        File[] papers = new File(fileName).listFiles();
        Score total = new Score();
        int totalIdCount = 0;
        for (int j = 0; j < Objects.requireNonNull(papers).length; j++) {
            if (!papers[j].toString().equals(fileName + "/.DS_Store")) {
                File[] paperSections = papers[j].listFiles();
                for (int k = 0; k < Objects.requireNonNull(paperSections).length; k++) {
                    File paperSection = paperSections[k];
                    if (!paperSection.getName().contains("Electronic supp")) {
                        Xml xml = new Xml(paperSection);
                        HashMap<String, ArrayList<Integer>> map = new HashMap<>();
                        for (int i = 0; i < ontology.size(); i++) {
                            Term term = ontology.getTerm(i);
                            map.putAll(term.findMatches(xml.getText()));
                        }
                        ArrayList<AbstractMap.SimpleEntry<String, Integer>> tags = xml.getTags(ontology.getType());
                        if (!tags.isEmpty()) {
                            System.out.println(paperSection.getName());
                            Score current = new Score(tags, map);
                            System.out.println(current);
                            System.out.println("id count: " + tags.size());
                            totalIdCount += tags.size();
                            total.addFN(current.getFN());
                            total.addFP(current.getFP());
                            total.addTP(current.getTP());
                        } else {
                            System.out.println(paperSection.getName() + " file is empty for " + ontology.getType() + ".");
                        }
                    }
                }
                System.out.println(papers[j].getName() + " done.");
                System.out.println();
            }
        }
        System.out.println();
        System.out.println("Final Result:");
        System.out.println("Total Score: " + total);
        System.out.println("total id count: " + totalIdCount);
    }
}