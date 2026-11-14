package p;

public class Calc {
  sealed interface Expr<T> {}

  record Lit<T>(T value) implements Expr<T> {}

  static final class Neg implements Expr<Integer> {}
}
