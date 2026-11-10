package com.example;

import java.util.ArrayList;
import java.util.List;

public class Library {
  int count(List<Shelf> shelves) {
    List<String> all = new ArrayList<>();
    for (Shelf shelf : shelves) {
      all.addAll(shelf.titles());
    }
    return all.size();
  }

  record Shelf(List<String> titles) {}
}
