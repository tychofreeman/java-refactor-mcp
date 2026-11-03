package com.example.garden;

import com.example.Plant;
import java.util.ArrayList;
import java.util.List;

public class Bed {
  private final List<Plant> plants = new ArrayList<>();

  void add(Plant plant) {
    plants.add(plant);
  }
}
