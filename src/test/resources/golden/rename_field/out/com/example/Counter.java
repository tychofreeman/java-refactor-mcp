package com.example;

public class Counter {
    private int total;

    public int next() {
        total = total + 1;
        return total;
    }
}
