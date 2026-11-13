package com.example;

class Shelf {
    enum Level {
        LOW {
            @Override
            public int height() {
                return ordinal() + 1;
            }
        },
        HIGH {
            @Override
            public int height() {
                return 10;
            }
        };

        public abstract int height();
    }

    Level level = Level.LOW;
}
