package a;

public sealed interface Shape permits b.Circle {
  double area();
}
