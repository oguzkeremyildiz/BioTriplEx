package org.example;

import org.example.ner.DictionaryExtractor;
import org.example.ner.Entity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the indexed matcher against the Knuth-Morris-Pratt reference implementation it replaced, and pins the two
 * behaviours that differ on purpose: whole word matching and case sensitive gene symbols.
 */
public class DictionaryExtractorTest {

    @Test
    public void findsTheSameOffsetsAsKnuthMorrisPratt() {
        String text = "Deregulation of CEACAM1 causes lung adenocarcinoma in lung cancer patients.";
        DictionaryExtractor extractor = new DictionaryExtractor();
        extractor.add("lung adenocarcinoma", Type.DISEASE, false);
        extractor.add("lung cancer", Type.DISEASE, false);
        extractor.add("CEACAM1", Type.GENE, true);
        for (Entity entity : extractor.extract(text)) {
            List<Integer> byKmp = Term.search(entity.getText(), text);
            assertTrue(byKmp.contains(entity.getStart()), entity + " was not found by KMP at " + entity.getStart());
        }
    }

    @Test
    public void reportsZeroBasedOffsets() {
        String text = "abc CEACAM1 def";
        assertEquals(List.of(4), Term.search("CEACAM1", text));
        DictionaryExtractor extractor = new DictionaryExtractor();
        extractor.add("CEACAM1", Type.GENE, true);
        assertEquals(4, extractor.extract(text).get(0).getStart());
    }

    @Test
    public void matchesWholeWordsOnly() {
        DictionaryExtractor extractor = new DictionaryExtractor();
        extractor.add("CEA", Type.GENE, true);
        assertTrue(extractor.extract("CEACAM1 is a gene").isEmpty(), "CEA must not fire inside CEACAM1");
        assertEquals(1, extractor.extract("the CEA family").size());
        assertFalse(Term.search("CEA", "CEACAM1 is a gene").isEmpty(), "KMP does match the substring");
    }

    @Test
    public void keepsGeneSymbolsCaseSensitive() {
        DictionaryExtractor extractor = new DictionaryExtractor();
        extractor.add("SET", Type.GENE, true);
        assertTrue(extractor.extract("we set the threshold").isEmpty());
        assertEquals(1, extractor.extract("SET is overexpressed").size());
    }

    @Test
    public void prefersTheLongestMatch() {
        DictionaryExtractor extractor = new DictionaryExtractor();
        extractor.add("cancer", Type.DISEASE, false);
        extractor.add("lung cancer", Type.DISEASE, false);
        List<Entity> found = new ArrayList<>(extractor.extract("a study of lung cancer"));
        assertEquals(1, found.size());
        assertEquals("lung cancer", found.get(0).getText());
    }
}
