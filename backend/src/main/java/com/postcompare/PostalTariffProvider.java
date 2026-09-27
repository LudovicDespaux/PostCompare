package com.postcompare;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import com.postcompare.Tariffs.*;

/** Every postal and express service described in tariffs.json. The customer posts the letter himself. */
@Component
public class PostalTariffProvider implements MailProvider {
    private final TariffCatalog tariffs;

    public PostalTariffProvider(TariffCatalog tariffs) { this.tariffs = tariffs; }

    @Override public List<Quote> quotes(QuoteRequest r) {
        boolean domestic = r.origin().equals(r.destination());
        List<Quote> out = new ArrayList<>();
        for (Carrier c : tariffs.catalog().carriers()) {
            if (c.online() || !c.origins().contains(r.origin())) continue;
            for (Service s : c.services()) {
                if (s.scope().equals("DOMESTIC") != domestic || (r.tracking() && !s.tracking())) continue;
                tariffs.zoneFor(s, r.destination()).ifPresent(z ->
                    TariffCatalog.priceFor(z, r.weight()).ifPresent(price -> out.add(quote(c, s, z, price))));
            }
        }
        return out;
    }

    private Quote quote(Carrier c, Service s, Zone z, BigDecimal price) {
        int min = z.minDays() != null ? z.minDays() : s.minDays();
        int max = z.maxDays() != null ? z.maxDays() : s.maxDays();
        String method = c.type().equals("EXPRESS") ? "EXPRESS" : "SELF_POST";
        String description = (method.equals("EXPRESS") ? "Enlèvement ou dépôt en point relais, livraison express."
                : "Lettre préparée et déposée par vos soins.") + " Zone : " + z.name() + "."
                + (s.registered() ? " Envoi recommandé avec signature." : "")
                + (c.note() != null ? " " + c.note() : "");
        return new Quote(c.id() + "-" + s.id(), c.name(), s.name(), method, tariffs.toEuro(price, c.currency()), "EUR",
            price, c.currency(), min, max, s.tracking(), description, "Affranchissement seul", c.priceBasis(),
            c.source(), c.validFrom());
    }
}
