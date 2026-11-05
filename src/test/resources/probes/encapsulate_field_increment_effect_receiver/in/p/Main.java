package p;
public class Main {
  static int made;
  static Box box = new Box();
  static Box get() { made++; return box; }
  public static void main(String[] args) {
    get().count++;
    get().count += 5;
    System.out.println(box.count + " " + made);
  }
}
class Box {
  int count;
}
