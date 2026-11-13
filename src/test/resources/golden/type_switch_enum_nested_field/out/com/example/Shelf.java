package com.example;

class Shelf {
    enum Level {
        LOW("l"),
        HIGH("h");

        private final String tag;

        Level(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    Level level = Level.LOW;

    String label() {
        return level.tag();
    }
}
