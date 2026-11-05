package warehouse.gates;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Admits trucks to the dock doors of the warehouse. */
public final class Yard {

  final List<String> doors; // door names, in the order they are tried
  final Map<String, String> taken = new HashMap<>(); // door to the truck at it
  final Set<String> paid = new HashSet<>();
  final List<String> charged = new ArrayList<>(); // trucks charged the dock fee, in order
  private final Deque<Integer> slots; // arrival slots still to hand out

  /** A yard with the given doors and arrival slots. */
  public Yard(List<String> doors, Integer... slots) {
    this.doors = doors;
    this.slots = new ArrayDeque<>(List.of(slots));
  }

  /** Whether a and b are both true. */
  static boolean both(boolean a, boolean b) {
    return a && b;
  }

  /** Whether a or b is true. */
  static boolean either(boolean a, boolean b) {
    return a || b;
  }

  /** Whether lo <= v <= hi. */
  static boolean within(int v, int lo, int hi) {
    return lo <= v && v <= hi;
  }

  /** Puts truck at the first free door, and returns whether there was one. */
  boolean reserve(String truck) {
    for (String d : doors) {
      if (!taken.containsKey(d)) {
        taken.put(d, truck);
        return true;
      }
    }
    return false;
  }

  /** Charges truck the dock fee, and returns whether it has paid. */
  boolean charge(String truck) {
    charged.add(truck);
    return paid.contains(truck);
  }

  /** Hands out the next arrival slot, or 0 when none is left. */
  int nextSlot() {
    Integer s = slots.poll();
    return s == null ? 0 : s;
  }

  /** Reserves a door for truck and charges it, and returns whether both succeeded. */
  public boolean admit(String truck) {
    return both(reserve(truck), charge(truck));
  }

  /** Lets a truck through when it is known or has paid. */
  public boolean pass(String truck, boolean known) {
    return either(known, paid.contains(truck));
  }

  /** Admits a truck that has paid or that can be charged. */
  public boolean express(String truck) {
    return either(paid.contains(truck), charge(truck));
  }

  /** Whether the next arrival slot is in the morning window. */
  public boolean onTime() {
    return within(nextSlot(), 6, 11);
  }

  /** Whether n pallets fit a door. */
  public static boolean fits(int n) {
    return within(n, 1, 24);
  }
}
