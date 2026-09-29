package org.example;

import org.example.text.Sentences;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sentence splitter has to return slices of the original text, because both the relation scope and the transformer
 * windows are addressed with the offsets it reports.
 */
public class SentencesTest {

    @Test
    public void rangesAreSlicesOfTheInput() {
        String text = "TP53 is mutated. EGFR is amplified in lung cancer.";
        for (int[] range : Sentences.split(text)) {
            assertTrue(range[0] >= 0 && range[1] <= text.length());
            assertFalse(text.substring(range[0], range[1]).isBlank());
        }
    }

    @Test
    public void splitsOnSentenceEnds() {
        List<int[]> sentences = Sentences.split("TP53 is mutated. EGFR is amplified.");
        assertEquals(2, sentences.size());
        assertEquals("TP53 is mutated.", slice("TP53 is mutated. EGFR is amplified.", sentences.get(0)));
    }

    @Test
    public void keepsDecimalsAndAbbreviationsTogether() {
        String text = "Expression was higher (P < 0.05) in the tumour. See Fig. 2 for details.";
        List<int[]> sentences = Sentences.split(text);
        assertEquals(2, sentences.size());
        assertTrue(slice(text, sentences.get(0)).contains("0.05"));
        assertTrue(slice(text, sentences.get(1)).contains("Fig. 2"));
    }

    @Test
    public void findsTheEnclosingSentence() {
        String text = "TP53 is mutated. EGFR is amplified.";
        List<int[]> sentences = Sentences.split(text);
        assertArrayEquals(sentences.get(1), Sentences.enclosing(sentences, text.indexOf("EGFR")));
    }

    private static String slice(String text, int[] range) {
        return text.substring(range[0], range[1]);
    }
}
