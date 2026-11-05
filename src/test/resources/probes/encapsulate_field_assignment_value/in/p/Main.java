package p;
public class Main {
  public static void main(String[] args) {
    Box b = new Box();
    int y = (b.count = 4) + 1;
    int z = b.count++;
    System.out.println(y + " " + z + " " + b.count);
  }
}
class Box {
  int count;
}
