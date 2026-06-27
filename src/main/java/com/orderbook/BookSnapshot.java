package com.orderbook;

import java.util.List;

public record BookSnapshot(List<LevelSnapshot> bids, List<LevelSnapshot> asks) {}
