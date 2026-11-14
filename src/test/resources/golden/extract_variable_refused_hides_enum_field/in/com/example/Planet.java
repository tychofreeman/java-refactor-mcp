package com.example;

public enum Planet {
  EARTH(6.0);

  final double mass;

  Planet(double mass) {
    this.mass = mass;
  }

  double g(double r) {
    double k = r * r;
    return mass / k;
  }
}
