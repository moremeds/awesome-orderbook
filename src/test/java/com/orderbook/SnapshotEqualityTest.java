package com.orderbook;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotEqualityTest {

    @Test
    void structurallyEqualSnapshotsAreEqual() {
        BookSnapshot a = new BookSnapshot(
            List.of(new LevelSnapshot(100, 30, List.of(new OrderSnapshot(1, 10), new OrderSnapshot(2, 20)))),
            List.of());
        BookSnapshot b = new BookSnapshot(
            List.of(new LevelSnapshot(100, 30, List.of(new OrderSnapshot(1, 10), new OrderSnapshot(2, 20)))),
            List.of());
        assertEquals(a, b);
    }

    @Test
    void fifoOrderIsSignificant() {
        LevelSnapshot a = new LevelSnapshot(100, 30, List.of(new OrderSnapshot(1, 10), new OrderSnapshot(2, 20)));
        LevelSnapshot b = new LevelSnapshot(100, 30, List.of(new OrderSnapshot(2, 20), new OrderSnapshot(1, 10)));
        assertNotEquals(a, b);
    }
}
