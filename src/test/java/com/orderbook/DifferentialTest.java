package com.orderbook;

import com.orderbook.reference.TreeMapOrderBook;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Applies identical random valid op sequences to the primary and the reference oracle,
 * comparing normalized snapshots after each op and validating the primary's invariants.
 * Reproducible: each scenario is keyed by a fixed seed.
 */
class DifferentialTest {

    private static final int SCENARIOS = 200;
    private static final int OPS_PER_SCENARIO = 2000;
    private static final long PRICE_MIN = 100, PRICE_MAX = 120;

    @Test
    void randomizedSequencesMatchReference() {
        for (long seed = 0; seed < SCENARIOS; seed++) runScenario(seed, OPS_PER_SCENARIO);
    }

    private void runScenario(long seed, int ops) {
        Random rnd = new Random(seed);
        ActiveArrayOrderBook primary = new ActiveArrayOrderBook();
        TreeMapOrderBook reference = new TreeMapOrderBook();
        List<Long> live = new ArrayList<>();
        List<String> history = new ArrayList<>();
        long nextId = 1;

        for (int op = 0; op < ops; op++) {
            int roll = rnd.nextInt(100);
            String desc;
            if (roll < 45 || live.isEmpty()) {                 // ADD
                long id = nextId++;
                Side side = rnd.nextBoolean() ? Side.BID : Side.ASK;
                long price = PRICE_MIN + rnd.nextInt((int) (PRICE_MAX - PRICE_MIN + 1));
                long qty = 1 + rnd.nextInt(100);
                desc = "ADD id=" + id + " " + side + " p=" + price + " q=" + qty;
                primary.add(id, side, price, qty);
                reference.add(id, side, price, qty);
                live.add(id);
            } else if (roll < 70) {                             // UPDATE (>0)
                long id = live.get(rnd.nextInt(live.size()));
                long qty = 1 + rnd.nextInt(100);
                desc = "UPDATE id=" + id + " q=" + qty;
                primary.update(id, qty);
                reference.update(id, qty);
            } else if (roll < 85) {                             // UPDATE -> 0 (remove)
                long id = live.remove(rnd.nextInt(live.size()));
                desc = "UPDATE0 id=" + id;
                primary.update(id, 0);
                reference.update(id, 0);
            } else if (roll < 95) {                             // REMOVE
                long id = live.remove(rnd.nextInt(live.size()));
                desc = "REMOVE id=" + id;
                primary.remove(id);
                reference.remove(id);
            } else {                                            // TRIM
                Side side = rnd.nextBoolean() ? Side.BID : Side.ASK;
                int keep = rnd.nextInt(8);
                desc = "TRIM " + side + " keep=" + keep;
                primary.trim(side, keep);
                reference.trim(side, keep);
                live.removeIf(id -> !reference.snapshotContains(id));
            }
            history.add(desc);
            if (history.size() > 12) history.remove(0);

            String ctx = "seed=" + seed + " op=" + op + " last=" + desc + "\nrecent=" + history;
            assertEquals(reference.snapshot(), primary.snapshot(), ctx);
            primary.validateInvariants();
        }
    }
}
