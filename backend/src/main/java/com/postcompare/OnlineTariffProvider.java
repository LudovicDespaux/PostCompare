package com.postcompare;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import com.postcompare.Tariffs.*;

/**
 * Online print-and-mail services described in tariffs.json (type ONLINE). The document is uploaded from
 * anywhere; "domestic" means the destination is the country the service posts from.
 */
@Component
public class OnlineTariffProvider implements MailProvider {
    private final TariffCatalog tariffs;

    public OnlineTariffProvider(TariffCatalog tariffs) { this.tariffs = tariffs; }

    @Override public List<Quote> quotes(QuoteRequest r) {
        List<Quote> out = new ArrayList<>();
        for (Carrier c : tariffs.catalog().carriers()) {
            if (!c.online()) continue;
            // Public LetterStream pricing also depends on printed sides and postage weight.
            // Until modeled completely, only expose the verified one-page monochrome rate.
            if (c.id().equals("letterstream") && (r.pages() != 1 || r.color())) continue;
            boolean domestic = r.destination().equals(c.postsFrom());
            for (Service s : c.services()) {
                if (s.scope().equals("DOMESTIC") != domestic || (r.tracking() && !s.tracking())) continue;
                int units = s.unit().equals("PAGE") ? r.pages() : PrintAndMailProvider.sheets(r);
                if (units > s.max()) continue;
                tariffs.zoneFor(s, r.destination())
                    .filter(z -> !r.color() || (z.colorBase() != null && z.colorExtra() != null))
                    .ifPresent(z -> out.add(quote(c, s, z, r, units)));
            }
        }
        return out;
    }

    private Quote quote(Carrier c, Service s, Zone z, QuoteRequest r, int units) {
        boolean colorPriced = z.colorBase() != null;
        boolean color = r.color() && colorPriced;
        BigDecimal base = color ? z.colorBase() : z.base();
        BigDecimal extra = color ? z.colorExtra() : z.extra();
        BigDecimal price = base.add(extra.multiply(BigDecimal.valueOf(Math.max(0, units - s.included()))));
        int min = z.minDays() != null ? z.minDays() : s.minDays();
        int max = z.maxDays() != null ? z.maxDays() : s.maxDays();
        String description = "Vous téléversez le document ; il est imprimé et posté depuis "
            + new Locale("", c.postsFrom()).getDisplayCountry(Locale.FRENCH) + ". Zone : " + z.name() + "."
            + (s.registered() ? " Envoi recommandé." : "")
            + (r.color() && !colorPriced ? " Surcoût couleur non précisé sur la grille publique." : "")
            + (c.note() != null ? " " + c.note() : "");
        return new Quote(c.id() + "-" + s.id(), c.name(), s.name(), "PRINT_AND_MAIL", tariffs.toEuro(price, c.currency()),
            "EUR", price, c.currency(), min, max, s.tracking(), description, "Impression + enveloppe + affranchissement",
            c.priceBasis(), c.source(), c.validFrom());
    }
}
