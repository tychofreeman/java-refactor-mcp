package com.example;

sealed interface Shape permits Circle, Square {
  double area();
}
