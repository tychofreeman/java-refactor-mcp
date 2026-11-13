package com.example;

public sealed interface Shape {
  record Circle(double r) implements Shape {}

  record Square(double side) implements Shape {}
}
