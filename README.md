# BioTriplEx

Software for constructing a knowledge graph from an XML formatted biomedical dataset.

The pipeline reads the annotated BioTriplEx corpus, finds the genes and the diseases in the text,
reads the relation stated between them, and merges every statement of the corpus into one graph that
can be browsed and exported.

```
XML section ──▶ entity extraction ──▶ relation extraction ──▶ knowledge graph ──▶ graph view
                (ontology or BioNER)   (sentence + trigger)     (merged nodes)
```

## What changed after CS 401

The CS 401 prototype was the deterministic baseline: a Knuth-Morris-Pratt matcher over DOID and
HG_PCO behind a Swing window. This version implements the three items of the Future Work slide that make up the extraction and the graph.

| Future Work item | Where it lives |
|---|---|
| Move from rule based matching to a machine learning model, BioBERT through DJL and HuggingFace | `ner/BioNerExtractor`, `ner/BioNerModel` |
| Relation extraction to identify the links between nodes | `kg/RelationExtractor`, `kg/RelationLexicon` |
| A dynamic graph rendering engine and interactive exploration | `ui/GraphPanel` with GraphStream |

## Running it

Java 17 or newer and Maven. The desktop application:

```bash
mvn -q compile exec:java
```

The command line goes through `run.sh`, which compiles and builds the classpath on first use:

```bash
./run.sh help
```

| Command | What it does |
|---|---|
| `ui` | Opens the desktop application, the default when no command is given |
| `evaluate [--methods dictionary,bioner,both] [--on test]` | Scores extraction against the gold tags |
| `graph [--method bioner] [--min-support 2]` | Builds the corpus graph and exports it |
| `lexicon` | Re-mines the relation trigger lexicon from the corpus |

The BioNER models are downloaded from HuggingFace on first use, about 180 MB in total, and cached in
`models/`. Add `-Dbiotriplex.model.precision=fp32` for the full precision weights instead of the
quantized ones, or `-Dbiotriplex.models.dir=/some/path` to share one cache between checkouts.

## BioNER

The CS 401 code pointed DJL at `https://huggingface.co/<model>` and asked the PyTorch engine to load
it. That cannot work: the PyTorch engine only loads TorchScript archives, while a HuggingFace
repository holds a `pytorch_model.bin` state dict, and the gene model it named,
`monologg/biobert_v1.1_pubmed_ner`, does not exist on the hub at all.

Two BioBERT derived token classifiers exported to ONNX are used instead, so the ONNX Runtime engine
executes them straight from the hub with no Python step anywhere in the build:

- `OpenMed/OpenMed-NER-DiseaseDetect-PubMed-109M-v1` for diseases, labels `O, B-DISEASE, I-DISEASE`
- `OpenMed/OpenMed-NER-GenomeDetect-PubMed-109M-v1` for genes, labels `O, B-GENE/PROTEIN, I-GENE/PROTEIN`

A document is packed into windows of at most 512 tokens along sentence boundaries; each window is a
verbatim slice of the text, so the character offsets the tokenizer reports only need the window start
added to land on the coordinate system the gold annotations use. Sub-word pieces are folded into
words by word id and a word takes the label of its first piece, the `aggregation_strategy="first"`
rule of the HuggingFace pipeline.

## Offsets

The `spans="start~end"` attributes index into the CDATA content of `<TEXT>` **including its leading
newline**. `Xml` keeps that text verbatim for exactly this reason. The old parser rebuilt the text
line by line and dropped the newline, which shifted every offset by one; the KMP matcher compensated
by returning `i - j + 1` instead of `i - j`. Both halves of that are fixed, and `XmlTest` checks all
22 000 gold spans of the corpus resolve to their annotated surface form.

## Relation extraction

The scope is the sentence: 1134 of the 1199 gold links keep the gene, the disease and the relation
trigger inside one sentence. Inside a sentence every gene is paired with every disease and labelled
with the closest trigger from the lexicon.

The lexicon is not hand written. `RelationLexicon.fromCorpus` counts every `<RELATION>` annotation,
keeps the majority label per trigger and drops the ones annotated "no relation"; discontinuous
annotations like `high ... expression` become a two part pattern. `src/main/resources/relation-lexicon.tsv`
ships 351 triggers mined from the **training** papers only, and the trigger distance threshold was
picked on the training split too (`--on train`), so the numbers below are measured on papers neither
the lexicon nor the threshold ever saw.

## Results

100 papers, 600 sections, split 70/30 at paper level. Held out test set: 30 papers, 177 sections.
Entity scores are micro averaged; triple scores match endpoints by span overlap.

| Method | P | R | F1 | F1 overlap | Triple pair F1 | Triple labelled F1 | Time |
|---|---|---|---|---|---|---|---|
| Exact match (ontology) | 0.667 | 0.529 | 0.590 | 0.615 | 0.254 | 0.181 | 1.3 s |
| BioNER (BioBERT) | 0.496 | 0.797 | 0.612 | 0.726 | 0.174 | 0.118 | 188 s |
| Ontology + BioNER | 0.460 | 0.867 | 0.601 | 0.659 | 0.141 | 0.095 | 201 s |

Per type, BioNER scores F1 0.584 on genes and 0.659 on diseases; the ontology matcher scores 0.618
and 0.552.

What the numbers say:

- **BioNER trades precision for recall.** It finds 80% of the gold mentions against the ontology's
  53%, and at overlap matching it reaches 0.946 recall, so almost every gold mention is at least
  touched. The remaining gap is boundary disagreement, not blindness: exact-span F1 rises from 0.612
  to 0.726 when boundaries are relaxed.
- **The ontology matcher is precise and 150 times faster**, because it only reports what is already
  in DOID or HG_PCO. It is the better choice when a curated vocabulary is the requirement.
- **The union is worse than either.** More candidate mentions mean quadratically more candidate pairs
  in a sentence, so triple precision falls fastest there.
- **Triple extraction is the weak link, and honestly so.** The annotators link a specific gene to a
  specific disease; pairing every gene with every disease in the sentence over-generates badly, the
  more so the more mentions the entity step returns. This is the part to attack next, with a
  supervised relation classifier over the 1199 gold links rather than a distance rule.

## Knowledge graph

Mentions are merged by normalized surface form, so every paper that writes CEACAM1 lands on the same
node and an edge weight is the number of sentences in the corpus supporting that statement. Every
edge keeps its supporting sentences, which is what makes the graph view clickable back to the text.

`graph --method bioner --min-support 2` over the whole corpus gives 1896 nodes and 3820 edges raw,
690 nodes and 1047 edges once single-sentence edges are dropped, in about 11 minutes. The best
connected nodes are HCC (degree 72), tumor (51), breast cancer (46), cancer (41) and miR-146a (26).
It writes GraphML for Cytoscape, Gephi and yEd, a node/edge CSV pair, JSON, a Cypher script for
Neo4j and a rendered PNG.

In the desktop application the graph tab draws the graph of the text that was just extracted: a force
directed layout, blue genes and red diseases, node size by degree, edge labels carrying the predicate
and its support count, and a details pane that lists the supporting sentences of whichever node is
clicked. `GraphPanel.writeImage` renders through the same renderer and stylesheet without opening a
window, which is what the `graph` command and `GraphRenderingTest` use.

## Layout

```
org.example            Corpus, Xml, Ontology, Term, Score, Pipeline, Main
org.example.ner        Entity, EntityExtractor, DictionaryExtractor, BioNerExtractor, BioNerModel
org.example.kg         Triple, RelationMention, EntityLink, RelationExtractor, RelationLexicon,
                       KnowledgeGraph, GraphExporter
org.example.eval       SpanMatcher, TripleMatcher, GoldTriples, CorpusEvaluator
org.example.text       Sentences
org.example.ui         BioTriplExFrame, GraphPanel
```

`Term.findMatches` keeps the original KMP matcher as the reference implementation; the pipeline uses
the indexed matcher in `DictionaryExtractor`, which probes only the word n-grams that occur in the
text instead of running one KMP pass per ontology term. `DictionaryExtractorTest` checks the two
agree, and pins the two places they differ on purpose: whole word matching, so CEA no longer fires
inside CEACAM1, and case sensitive gene symbols, so SET does not fire on ordinary prose.

## Known limitations

- The ontology matcher inherits whatever DOID contains, including short synonyms such as `can` that
  become spurious disease nodes. The BioNER path does not have this problem.
- Relation extraction is a distance rule over a mined trigger lexicon, not a trained classifier.
- Triples are only drawn inside a sentence, so cross-sentence statements, about 5% of the gold links,
  are out of reach by construction.

## Tests

```bash
mvn test
```

31 tests covering the offset convention against the whole corpus, KMP against the indexed matcher,
sentence segmentation, the span and triple matchers, graph rendering, node selection and the busy
state of the window. Several of them exist because they caught real defects: the renderer rejected
a second predicate between the same two nodes, clicking a node selected nothing, a near miss on a
node fell through to the selection rectangle, node selection missed by a factor of two on a high
resolution screen, and a result handler that threw left every button in the window disabled for good.
