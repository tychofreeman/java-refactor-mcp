package p;
public class Main {
  static class Box { static String name() { return "nested"; } }
  public static void main(String[] args) { System.out.println(Item.name()); }
}
class Item { static String name() { return "item"; } }
