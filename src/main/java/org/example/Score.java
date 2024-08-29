package org.example;

import java.util.*;

public class Score {

    private int TP;
    private int FN;
    private int FP;

    public Score(ArrayList<AbstractMap.SimpleEntry<String, Integer>> actual, HashMap<String, ArrayList<Integer>> predicted) {
        this();
        System.out.println("FN: ");
        for (AbstractMap.SimpleEntry<String, Integer> entry : actual) {
            if (!predicted.containsKey(entry.getKey())) {
                this.FN++;
                System.out.println(entry);
            } else {
                if (!predicted.get(entry.getKey()).contains(entry.getValue())) {
                    this.FN++;
                    System.out.println(entry);
                } else {
                    this.TP++;
                    predicted.get(entry.getKey()).remove(entry.getValue());
                }
            }
        }
        System.out.println("total FN: " + this.FN);
        System.out.println("FP: ");
        for (String key : predicted.keySet()) {
            this.FP += predicted.get(key).size();
            if (!predicted.get(key).isEmpty()) {
                System.out.println(key + " -> " + predicted.get(key));
            }
        }
        System.out.println("total FP: " + this.FP);
    }

    public Score() {
        this.TP = 0;
        this.FN = 0;
        this.FP = 0;
    }

    public int getTP() {
        return TP;
    }

    public int getFN() {
        return FN;
    }

    public int getFP() {
        return FP;
    }

    public void addTP(int value) {
        this.TP += value;
    }

    public void addFN(int value) {
        this.FN += value;
    }

    public void addFP(int value) {
        this.FP += value;
    }

    public double getRecall() {
        return (TP + 0.00) / (TP + FN);
    }

    public double getPrecision() {
        return (TP + 0.00) / (TP + FP);
    }

    public double getF1() {
        double precision = getPrecision();
        double recall = getRecall();
        return (2 * recall * precision) / (precision + recall);
    }

    @Override
    public String toString() {
        double precision = getPrecision();
        double recall = getRecall();
        double F1 = getF1();
        return "Precision: " + precision + " Recall: " + recall + " F1-score: " + F1;
    }
}
