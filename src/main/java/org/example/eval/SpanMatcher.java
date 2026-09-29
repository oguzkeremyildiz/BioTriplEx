package org.example.eval;

import org.example.Score;
import org.example.Type;
import org.example.ner.Entity;

import java.util.*;

/**
 * Aligns predicted mentions with gold mentions and counts the result.
 * STRICT demands the exact character span, which is what the corpus annotates. OVERLAP accepts any intersection of the
 * same type and shows how much of the gap is boundary disagreement, "the tumour" against "tumour", rather than a
 * missed entity.
 */
public final class SpanMatcher {

    private SpanMatcher() {
    }

    /**
     * Scores every type.
     * @param gold Gold mentions.
     * @param predicted Predicted mentions.
     * @param mode Matching mode.
     * @return The alignment and its score.
     */
    public static MatchResult match(List<Entity> gold, List<Entity> predicted, Mode mode) {
        return match(gold, predicted, mode, null);
    }

    /**
     * Scores the mentions of one type.
     * @param gold Gold mentions.
     * @param predicted Predicted mentions.
     * @param mode Matching mode.
     * @param type Type to score, or null for every type.
     * @return The alignment and its score.
     */
    public static MatchResult match(List<Entity> gold, List<Entity> predicted, Mode mode, Type type) {
        List<Entity> goldOfType = filter(gold, type);
        List<Entity> predictedOfType = filter(predicted, type);
        boolean[] used = new boolean[goldOfType.size()];
        ArrayList<Entity> spurious = new ArrayList<>();
        ArrayList<Entity[]> matched = new ArrayList<>();
        for (Entity candidate : predictedOfType) {
            int hit = -1;
            for (int i = 0; i < goldOfType.size() && hit < 0; i++) {
                if (used[i]) {
                    continue;
                }
                Entity goldEntity = goldOfType.get(i);
                if (mode == Mode.STRICT ? goldEntity.equals(candidate) : goldEntity.overlaps(candidate)) {
                    hit = i;
                }
            }
            if (hit >= 0) {
                used[hit] = true;
                matched.add(new Entity[]{goldOfType.get(hit), candidate});
            } else {
                spurious.add(candidate);
            }
        }
        ArrayList<Entity> missed = new ArrayList<>();
        for (int i = 0; i < goldOfType.size(); i++) {
            if (!used[i]) {
                missed.add(goldOfType.get(i));
            }
        }
        return new MatchResult(new Score(matched.size(), spurious.size(), missed.size()), matched, missed, spurious);
    }

    private static List<Entity> filter(List<Entity> entities, Type type) {
        if (type == null) {
            return entities;
        }
        ArrayList<Entity> selected = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity.getType() == type) {
                selected.add(entity);
            }
        }
        return selected;
    }

    /**
     * How a prediction is allowed to line up with a gold mention.
     */
    public enum Mode {

        STRICT,
        OVERLAP
    }

    /**
     * A score together with the aligned, missed and spurious mentions behind it.
     */
    public static final class MatchResult {

        private final Score score;
        private final List<Entity[]> matched;
        private final List<Entity> missed;
        private final List<Entity> spurious;

        MatchResult(Score score, List<Entity[]> matched, List<Entity> missed, List<Entity> spurious) {
            this.score = score;
            this.matched = matched;
            this.missed = missed;
            this.spurious = spurious;
        }

        public Score getScore() {
            return score;
        }

        /**
         * Returns the aligned pairs.
         * @return Pairs of gold and predicted mention.
         */
        public List<Entity[]> getMatched() {
            return Collections.unmodifiableList(matched);
        }

        /**
         * Returns the gold mentions no prediction covered.
         * @return The false negatives.
         */
        public List<Entity> getMissed() {
            return Collections.unmodifiableList(missed);
        }

        /**
         * Returns the predictions with no gold counterpart.
         * @return The false positives.
         */
        public List<Entity> getSpurious() {
            return Collections.unmodifiableList(spurious);
        }
    }
}
