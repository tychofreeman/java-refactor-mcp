package p;
import java.util.ArrayDeque;
public class Main {
  static class Op {
    String text;
    Op(String text) {
      this.text = text;
    }
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
    while (!pending.held.isEmpty()) {
      calculator.keys += pending.pop().text;
    }
  }
  public static void main(String[] args) {
    Stack s = new Stack();
    s.held.push(new Op("a"));
    s.held.push(new Op("b"));
    Calculator c = new Calculator();
    drain(s, c);
    System.out.println(c.keys);
  }
}
