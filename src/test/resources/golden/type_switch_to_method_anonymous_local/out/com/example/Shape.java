package com.example;

interface Shape {
  default void show() {
    System.out.println("other");
  }
}
