package a;

public sealed interface Figure permits b.Circle {
  double area();
}
