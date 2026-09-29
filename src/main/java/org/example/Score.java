package org.example;

/**
 * Counts of true positives, false positives and false negatives, with the usual derived metrics.
 */
public class Score {

    private int truePositives;
    private int falsePositives;
    private int falseNegatives;

    public Score() {
        this(0, 0, 0);
    }

    /**
     * Initializes a score.
     * @param truePositives Number of correct predictions.
     * @param falsePositives Number of predictions with no gold counterpart.
     * @param falseNegatives Number of gold items no prediction covered.
     */
    public Score(int truePositives, int falsePositives, int falseNegatives) {
        this.truePositives = truePositives;
        this.falsePositives = falsePositives;
        this.falseNegatives = falseNegatives;
    }

    public int getTP() {
        return truePositives;
    }

    public int getFP() {
        return falsePositives;
    }

    public int getFN() {
        return falseNegatives;
    }

    public void addTP(int value) {
        truePositives += value;
    }

    public void addFP(int value) {
        falsePositives += value;
    }

    public void addFN(int value) {
        falseNegatives += value;
    }

    /**
     * Accumulates another score into this one, for micro averaging over a corpus.
     * @param other Score to add.
     */
    public void add(Score other) {
        truePositives += other.truePositives;
        falsePositives += other.falsePositives;
        falseNegatives += other.falseNegatives;
    }

    public int getGoldCount() {
        return truePositives + falseNegatives;
    }

    public int getPredictedCount() {
        return truePositives + falsePositives;
    }

    public double getPrecision() {
        return getPredictedCount() == 0 ? 0.0 : (double) truePositives / getPredictedCount();
    }

    public double getRecall() {
        return getGoldCount() == 0 ? 0.0 : (double) truePositives / getGoldCount();
    }

    public double getF1() {
        double precision = getPrecision();
        double recall = getRecall();
        return precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
    }

    @Override
    public String toString() {
        return String.format("P: %.4f  R: %.4f  F1: %.4f  (TP %d, FP %d, FN %d)", getPrecision(), getRecall(), getF1(), truePositives, falsePositives, falseNegatives);
    }
}
