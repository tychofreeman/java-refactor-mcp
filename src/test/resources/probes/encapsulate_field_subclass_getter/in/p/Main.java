package p;
public class Main {
  public static void main(String[] args) {
    Person p = new Student();
    System.out.println(p.name);
  }
}
class Person {
  String name = "person";
}
class Student extends Person {
  public String getName() { return "student"; }
}
