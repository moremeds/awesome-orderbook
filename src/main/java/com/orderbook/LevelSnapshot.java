package com.orderbook;

import java.util.List;

public record LevelSnapshot(long price, long totalQty, List<OrderSnapshot> orders) {}
