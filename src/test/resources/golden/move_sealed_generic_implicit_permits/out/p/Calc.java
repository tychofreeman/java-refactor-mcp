package p;

public class Calc {

  record Lit<T>(T value) implements Expr<T> {}

  static final class Neg implements Expr<Integer> {}
}
