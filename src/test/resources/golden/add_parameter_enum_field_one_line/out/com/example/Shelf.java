package com.example;

class Shelf {
    enum Level {
        LOW(0), HIGH(0);

        private final int height;

        Level(int height) {
            this.height = height;
        }

        public int height() {
            return height;
        }
    }

    Level level = Level.LOW;
}
