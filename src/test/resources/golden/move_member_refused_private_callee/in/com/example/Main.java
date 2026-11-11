package com.example;

public class Main {
    public static void main(String[] args) {
        System.out.println(renameAll("a"));
    }

    static String renameAll(String r) {
        return carryOut(r) + carryOut(r);
    }

    private static String carryOut(String r) {
        return r + "!";
    }
}
