package com.example;

class Shelf {
    enum Level {
        LOW,
        HIGH
    }

    Level level = Level.LOW;

    String label() {
        return switch (level) {
            case LOW -> "l";
            case HIGH -> "h";
        };
    }
}
