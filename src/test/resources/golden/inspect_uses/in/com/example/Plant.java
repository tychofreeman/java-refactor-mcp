package com.example;

public class Plant {
  String species;
  int height;

  Plant(String species) {
    this.species = species;
  }

  int water(int litres) {
    height += litres;
    return height;
  }
}
