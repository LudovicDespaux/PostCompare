package com.postcompare;

import java.math.BigDecimal;

/** price is converted to EUR; originalPrice/originalCurrency is what the carrier publishes. */
public record Quote(String id, String provider, String service, String method, BigDecimal price, String currency,
                    BigDecimal originalPrice, String originalCurrency, int minDays, int maxDays, boolean tracking,
                    String description, String priceScope, String priceBasis, String source, String validFrom) {}
