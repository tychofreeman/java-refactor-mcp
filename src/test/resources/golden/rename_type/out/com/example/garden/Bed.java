package com.example.garden;

import com.example.Herb;
import java.util.ArrayList;
import java.util.List;

public class Bed {
  private final List<Herb> plants = new ArrayList<>();

  void add(Herb plant) {
    plants.add(plant);
  }
}
