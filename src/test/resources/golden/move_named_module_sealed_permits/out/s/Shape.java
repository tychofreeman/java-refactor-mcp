package s;

public sealed interface Shape permits b.Circle {
  double area();
}
