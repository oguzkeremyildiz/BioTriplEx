package org.example;

import org.example.kg.EntityLink;
import org.example.kg.RelationMention;
import org.example.ner.Entity;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the offset convention of the corpus. Every gold span has to index into the CDATA content including its leading
 * newline, because the extractors report offsets on exactly that text.
 */
public class XmlTest {

    private static final String SECTION = Corpus.DEFAULT_ROOT + "/2807459/2807459_ABSTRACT.xml";

    @Test
    public void goldSpansIndexIntoTheText() throws Exception {
        Xml document = new Xml(new File(SECTION));
        assertFalse(document.getEntities().isEmpty());
        for (Entity entity : document.getEntities()) {
            assertEquals(entity.getText(), document.getText().substring(entity.getStart(), entity.getEnd()));
        }
    }

    @Test
    public void everyGoldSpanOfTheCorpusIsConsistent() throws Exception {
        Corpus corpus = Corpus.load(new File(Corpus.DEFAULT_ROOT));
        int checked = 0;
        for (Xml document : corpus.documents()) {
            for (Entity entity : document.getEntities()) {
                assertEquals(entity.getText(), document.getText().substring(entity.getStart(), entity.getEnd()));
                checked++;
            }
        }
        assertTrue(checked > 20000, "expected the whole corpus to be read, checked " + checked);
    }

    @Test
    public void readsTheKnownOffsetsOfTheAbstract() throws Exception {
        Xml document = new Xml(new File(SECTION));
        assertEquals("CEACAM1", document.getText().substring(136, 143));
        assertEquals("lung adenocarcinoma", document.getText().substring(99, 118));
    }

    @Test
    public void readsRelationsAndLinks() throws Exception {
        Xml document = new Xml(new File(SECTION));
        List<RelationMention> relations = document.getRelations();
        assertEquals(1, relations.size());
        assertEquals("dysregulation", relations.get(0).getLabel());
        assertEquals("deregulated", relations.get(0).getText());
        List<EntityLink> links = document.getLinks();
        assertEquals(4, links.size());
        assertEquals("CEACAM1", links.get(0).getGeneText());
        assertEquals("lung cancer", links.get(0).getDiseaseText());
        assertNotNull(document.getEntityById(links.get(0).getGeneId()));
        assertNotNull(document.getRelationById(links.get(0).getRelationId()));
    }

    @Test
    public void splitsDiscontinuousSpansIntoContiguousMentions() {
        String content = "<TEXT><![CDATA[\nprostate, bladder and colon cancer]]></TEXT>\n<TAGS>\n<DISEASE id=\"D0\" spans=\"1~9,29~35\" text=\"prostate ... cancer\" />\n</TAGS>";
        Xml document = new Xml("test.xml", content);
        List<Entity> entities = document.getEntities();
        assertEquals(2, entities.size());
        assertEquals("prostate", entities.get(0).getText());
        assertEquals("cancer", entities.get(1).getText());
    }
}
