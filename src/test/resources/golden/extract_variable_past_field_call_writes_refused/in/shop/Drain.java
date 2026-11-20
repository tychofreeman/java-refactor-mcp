package shop;

class Drain {
  static class Calculator {
    String keys = "";
  }

  static class Stack {
    Calculator shown;

    String pop() {
      return note();
    }

    String note() {
      shown.keys = "";
      return "x";
    }
  }

  static void drain(Stack pending, Calculator calculator) {
    calculator.keys += pending.pop();
  }
}
