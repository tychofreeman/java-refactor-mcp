package p;
public class Main {
  public static void main(String[] args) {
    Box<String> b = new Box<>();
    try {
      Object o = b.cast(new Object());
      System.out.println("cast passed " + (o != null));
    } catch (ClassCastException e) {
      System.out.println("cast failed");
    }
  }
}
