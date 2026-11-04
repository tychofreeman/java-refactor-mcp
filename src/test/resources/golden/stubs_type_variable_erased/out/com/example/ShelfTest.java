package com.example;

class ShelfTest {
  <T> void keeps(T item) {
    Shelf shelf = new Shelf();
    shelf.put(item);
  }
}
