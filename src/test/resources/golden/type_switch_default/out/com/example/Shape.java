package com.example;

sealed interface Shape permits Circle, Square {
  default double area() {
    return 0.0;
  }
}
