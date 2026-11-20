package shop;

import java.util.ArrayDeque;

class Drain {
  static class Op {
    String text = "x";
  }

  static class Stack {
    ArrayDeque<Op> held = new ArrayDeque<>();

    Op pop() {
      return held.pop();
    }
  }

  static class Calculator {
    String keys = "";
  }

  static void drain(Stack pending, Calculator calculator) {
    calculator.keys += pending.pop().text;
  }
}
