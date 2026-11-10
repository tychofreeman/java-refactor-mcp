package com.example;

import java.util.List;

public class Library {
  int count(List<Shelf> shelves) {
    List<String> all = shelves.stream().flatMap(shelf -> shelf.titles().stream()).toList();
    return all.size();
  }

  record Shelf(List<String> titles) {}
}
