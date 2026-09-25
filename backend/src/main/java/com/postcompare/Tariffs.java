package com.postcompare;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** JSON model of backend/src/main/resources/tariffs.json (public rates collected by hand). */
public final class Tariffs {
    private Tariffs() {}

    public record Catalog(String collectedOn, Fx fx, Map<String, List<String>> groups, List<Carrier> carriers) {}

    public record Fx(String date, String source, String secondarySource, Map<String, BigDecimal> perEuro) {}

    /**
     * type: POSTAL or EXPRESS (the sender posts from one of {@code origins}), or ONLINE (document uploaded
     * from anywhere, {@code origins} = ["*"], printed and posted from {@code postsFrom}).
     */
    public record Carrier(String id, String name, String type, List<String> origins, String postsFrom, String currency,
                          String priceBasis, String source, String validFrom, String note, List<Service> services) {
        boolean online() { return "ONLINE".equals(type); }
    }

    /** unit/included/max only for ONLINE services: price = base + extra × (units − included). */
    public record Service(String id, String name, String scope, boolean tracking, boolean registered,
                          Integer minDays, Integer maxDays, String unit, Integer included, Integer max, List<Zone> zones) {}

    /** rates: [max grams, price] brackets (postal); base/extra/colorBase/colorExtra: online services. */
    public record Zone(String name, List<String> countries, List<String> exclude, Integer minDays, Integer maxDays,
                       List<List<BigDecimal>> rates, BigDecimal base, BigDecimal extra,
                       BigDecimal colorBase, BigDecimal colorExtra) {}
}
