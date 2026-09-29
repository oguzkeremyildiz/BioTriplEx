package org.example.eval;

import org.example.Score;
import org.example.kg.Triple;

import java.util.*;

/**
 * Scores extracted triples against the gold links.
 * Endpoints are matched by span overlap rather than exact offsets: when BioNER tags CEACAM1-4L where the annotator
 * tagged CEACAM1 the link is still the same statement, and holding relation extraction to the boundary decisions of
 * the entity step would only measure that step again.
 */
public final class TripleMatcher {

    private TripleMatcher() {
    }

    /**
     * Aligns predicted triples with the gold links.
     * @param gold Gold triples.
     * @param predicted Predicted triples.
     * @param mode Whether the relation type has to agree as well.
     * @return The alignment and its score.
     */
    public static MatchResult match(List<Triple> gold, List<Triple> predicted, Mode mode) {
        boolean[] used = new boolean[gold.size()];
        ArrayList<Triple> spurious = new ArrayList<>();
        ArrayList<Triple[]> matched = new ArrayList<>();
        for (Triple candidate : predicted) {
            int hit = -1;
            for (int i = 0; i < gold.size() && hit < 0; i++) {
                if (used[i]) {
                    continue;
                }
                Triple goldTriple = gold.get(i);
                if (!goldTriple.getSubject().overlaps(candidate.getSubject()) || !goldTriple.getObject().overlaps(candidate.getObject())) {
                    continue;
                }
                if (mode == Mode.LABELLED && !goldTriple.getPredicate().equalsIgnoreCase(candidate.getPredicate())) {
                    continue;
                }
                hit = i;
            }
            if (hit >= 0) {
                used[hit] = true;
                matched.add(new Triple[]{gold.get(hit), candidate});
            } else {
                spurious.add(candidate);
            }
        }
        ArrayList<Triple> missed = new ArrayList<>();
        for (int i = 0; i < gold.size(); i++) {
            if (!used[i]) {
                missed.add(gold.get(i));
            }
        }
        return new MatchResult(new Score(matched.size(), spurious.size(), missed.size()), matched, missed, spurious);
    }

    /**
     * How strictly a predicted triple has to agree with a gold link.
     */
    public enum Mode {

        PAIR,
        LABELLED
    }

    /**
     * A score together with the aligned, missed and spurious triples behind it.
     */
    public static final class MatchResult {

        private final Score score;
        private final List<Triple[]> matched;
        private final List<Triple> missed;
        private final List<Triple> spurious;

        MatchResult(Score score, List<Triple[]> matched, List<Triple> missed, List<Triple> spurious) {
            this.score = score;
            this.matched = matched;
            this.missed = missed;
            this.spurious = spurious;
        }

        public Score getScore() {
            return score;
        }

        public List<Triple[]> getMatched() {
            return Collections.unmodifiableList(matched);
        }

        public List<Triple> getMissed() {
            return Collections.unmodifiableList(missed);
        }

        public List<Triple> getSpurious() {
            return Collections.unmodifiableList(spurious);
        }
    }
}
