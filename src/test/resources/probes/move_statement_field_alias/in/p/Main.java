package p;
public class Main {
  int v;
  public static void main(String[] args) {
    Main p = new Main();
    Main q = p;
    int seen = 0;
    p.v = 1;
    seen = q.v;
    System.out.println(seen);
  }
}
