package com.example;

public class Renamer {
    private final String name;

    Renamer(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    static String carryOut(String r) {
        if (r.isEmpty()) {
            return "none";
        }
        return r + "!";
    }
}
