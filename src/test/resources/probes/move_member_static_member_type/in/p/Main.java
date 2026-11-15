package p;

public class Main {
  public static class Stack {
    @Override
    public String toString() {
      return "Main.Stack";
    }
  }

  static Object fresh() {
    return new Stack();
  }

  public static void main(String[] args) {
    System.out.println(fresh());
  }
}

class Piles {}
