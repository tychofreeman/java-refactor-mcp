package com.example;

class Store {
    int height(Shelf.Level l) {
        return switch (l) {
            case LOW -> l.ordinal() + 1;
            case HIGH -> 10;
        };
    }
}
