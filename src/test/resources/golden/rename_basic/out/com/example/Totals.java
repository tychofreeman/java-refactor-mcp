package com.example;

public class Totals {
    public int sum(int[] values) {
        int runningSum = 0; // running total
        for (int v : values) {
            runningSum += v;
        }
        String label = "total";
        return runningSum;
    }
}
