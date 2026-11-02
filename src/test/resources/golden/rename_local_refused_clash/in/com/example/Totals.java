package com.example;

public class Totals {
    public int sum(int[] values) {
        int total = 0; // running total
        for (int v : values) {
            total += v;
        }
        String label = "total";
        return total;
    }
}
