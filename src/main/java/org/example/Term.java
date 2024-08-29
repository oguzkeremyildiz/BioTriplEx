package org.example;
import java.util.*;

public class Term {

    private final HashMap<String, ArrayList<String>> attributeMap;
    private final Type type;

    public Term(ArrayList<String> lines, Type type) {
        this.type = type;
        this.attributeMap = new HashMap<>();
        for (String line : lines) {
            String key = line.substring(0, line.indexOf(":"));
            String value;
            if (!key.equals("synonym")) {
                value = line.substring(line.indexOf(":") + 2);
            } else {
                value = line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""));
            }
            if (!attributeMap.containsKey(key)) {
                attributeMap.put(key, new ArrayList<>());
            }
            attributeMap.get(key).add(value);
        }
    }

    private static void computeLPSArray(String pat, int M, int[] lps) {
        int len = 0;
        lps[0] = 0;
        int i = 1;
        while (i < M) {
            if (pat.charAt(i) == pat.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
    }

    private static ArrayList<Integer> search(String pat, String txt) {
        int M = pat.length();
        int N = txt.length();
        int[] lps = new int[M];
        ArrayList<Integer> result = new ArrayList<>();
        computeLPSArray(pat, M, lps);
        int i = 0;
        int j = 0;
        while ((N - i) >= (M - j)) {
            if (pat.charAt(j) == txt.charAt(i)) {
                j++;
                i++;
            }
            if (j == M) {
                result.add(i - j + 1);
                j = lps[j - 1];
            } else if (i < N && pat.charAt(j) != txt.charAt(i)) {
                if (j != 0) {
                    j = lps[j - 1];
                }
                else {
                    i = i + 1;
                }
            }
        }
        return result;
    }

    private void addMatches(HashMap<String, ArrayList<Integer>> matches, ArrayList<String> list, String text) {
        for (String key : list) {
            ArrayList<Integer> match = search(key, text);
            if (!match.isEmpty()) {
                matches.put(key, match);
            }
        }
    }

    public HashMap<String, ArrayList<Integer>> findMatches(String text) {
        HashMap<String, ArrayList<Integer>> matches = new HashMap<>();
        switch (type) {
            case GENE:
                addMatches(matches, attributeMap.get("id"), text);
                addMatches(matches, attributeMap.get("name"), text);
                break;
            case DISEASE:
                if (attributeMap.containsKey("synonym")) {
                    addMatches(matches, attributeMap.get("synonym"), text);
                }
                addMatches(matches, attributeMap.get("name"), text);
                break;
            default:
                // to do
                break;
        }
        return matches;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String key : attributeMap.keySet()) {
            ArrayList<String> values = attributeMap.get(key);
            for (String value : values) {
                sb.append(key).append(" -> ").append(value).append("\n");
            }
        }
        return sb.toString();
    }
}
