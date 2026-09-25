package com.postcompare;

import java.math.BigDecimal;
import java.util.Optional;

/** Fictional providers and prices: never represent these as commercial quotes. */
public record DemoProvider(String id, String name, boolean digital, int baseCents,
                           int days, boolean supportsTracking) implements MailProvider {
    @Override public Optional<Quote> quote(QuoteRequest r) {
        if (r.tracking() && !supportsTracking) return Optional.empty();
        int sheets = r.duplex() ? (r.pages() + 1) / 2 : r.pages();
        int weight = digital ? sheets * 5 + 6 : r.weight();
        if (weight > 250) return Optional.empty();
        boolean international = !r.origin().equals(r.destination());
        int cents = baseCents + (digital ? r.pages() * (r.color() ? 22 : 8) + sheets * 3 : 0)
            + (international ? (digital ? 65 : 150) : 0)
            + ((weight - 1) / 20) * 35 + (r.tracking() ? 180 : 0);
        int min = days + (international ? (digital ? 1 : 3) : 0);
        return Optional.of(new Quote(id, name, digital ? "PRINT_AND_MAIL" : "SELF_POST",
            BigDecimal.valueOf(cents, 2), "EUR", min, min + 3, r.tracking(),
            digital ? "Impression, mise sous enveloppe et affranchissement simulés."
                    : "Lettre préparée et déposée par vos soins dans le pays de départ.",
            digital ? "Impression + enveloppe + port" : "Affranchissement seul"));
    }
}
