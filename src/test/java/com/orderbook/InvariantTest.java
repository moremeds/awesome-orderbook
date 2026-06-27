package com.orderbook;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvariantTest {

    @Test
    void invariantsHoldAfterMixedOps() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        book.add(1, Side.BID, 100, 10);
        book.add(2, Side.BID, 101, 5);
        book.add(3, Side.BID, 100, 7);
        book.add(4, Side.ASK, 103, 4);
        book.update(1, 12);
        book.update(3, 0);     // remove, level 100 keeps order 1
        book.remove(2);        // empties level 101
        book.add(5, Side.ASK, 102, 9);
        assertDoesNotThrow(book::validateInvariants);
    }

    @Test
    void invariantsHoldAfterTrim() {
        ActiveArrayOrderBook book = new ActiveArrayOrderBook();
        for (long p = 100; p < 120; p++) book.add(p, Side.BID, p, 1);
        book.trim(Side.BID, 5);
        assertDoesNotThrow(book::validateInvariants);
        assertEquals(5, book.levelCount(Side.BID));
    }

    @Test
    void emptyBookIsValid() {
        assertDoesNotThrow(new ActiveArrayOrderBook()::validateInvariants);
    }
}
