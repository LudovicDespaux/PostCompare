package com.postcompare;

import java.math.BigDecimal;

public record Quote(String id, String provider, String method, BigDecimal price, String currency,
                    int minDays, int maxDays, boolean tracking, String description, String priceScope) {}
