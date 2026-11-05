package warehouse.stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Counts the goods in each bin of the warehouse and records every move. */
public final class Ledger {

  public final Map<String, Integer> bins = new HashMap<>();
  public final List<String> moves = new ArrayList<>();

  /** Takes qty goods out of one bin and puts them in another. */
  public void transfer(String from, String to, int qty) {
    bins.merge(from, -qty, Integer::sum);
    bins.merge(to, qty, Integer::sum);
    moves.add(from + ">" + to + ":" + qty);
  }

  /** Moves qty goods from the dock into each of targets. */
  public void receive(List<String> targets, int qty) {
    for (String b : targets) {
      transfer("dock", b, qty);
    }
  }

  /** Moves qty goods from bin to the outbound bay. */
  public void ship(String bin, int qty) {
    transfer(bin, "bay", qty);
  }

  /** Moves goods sent back from the bay into bin. */
  public void giveBack(String bin, int qty) {
    transfer("bay", bin, qty);
  }

  /** Empties each of sources into target. */
  public void consolidate(List<String> sources, String target) {
    for (String b : sources) {
      if (!b.equals(target) && bins.getOrDefault(b, 0) > 0) {
        transfer(b, target, bins.get(b));
      }
    }
  }

  /** Makes each move of a pick list. */
  public void apply(PickList p) {
    while (p.more()) {
      transfer(p.bin(), p.bin(), p.qty());
    }
  }
}
