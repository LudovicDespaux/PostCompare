package com.postcompare;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import com.postcompare.Tariffs.*;

/**
 * Online services that print, envelope and post the letter from France. They work whatever the
 * sender's country, since the document is uploaded. Prices read on the providers' public pages.
 */
@Component
public class PrintAndMailProvider implements MailProvider {
    static final String COLLECTED = "2026-09-25";
    private final TariffCatalog tariffs;

    public PrintAndMailProvider(TariffCatalog tariffs) { this.tariffs = tariffs; }

    static int sheets(QuoteRequest r) { return r.duplex() ? (r.pages() + 1) / 2 : r.pages(); }
    /** 80 g/m² A4 sheet ≈ 5 g, envelope ≈ 6 g (C5) or 15 g (C4 beyond 5 sheets). */
    static int weight(QuoteRequest r) { int s = sheets(r); return s * 5 + (s > 5 ? 15 : 6); }

    @Override public List<Quote> quotes(QuoteRequest r) {
        List<Quote> out = new ArrayList<>();
        merciFacteur(r).ifPresent(out::add);
        eLettreRouge(r).ifPresent(out::add);
        return out;
    }

    private Optional<Quote> merciFacteur(QuoteRequest r) {
        // Do not rank an unknown color supplement as if it were zero.
        if (r.color()) return Optional.empty();
        boolean domestic = r.destination().equals("FR");
        Carrier laPoste = tariffs.carrier("laposte").orElseThrow();
        Service postage = laPoste.services().stream()
            .filter(s -> s.id().equals(domestic ? "lettre-verte" : "internationale")).findFirst().orElseThrow();
        Optional<BigDecimal> stamp = tariffs.zoneFor(postage, r.destination())
            .flatMap(z -> TariffCatalog.priceFor(z, weight(r)));
        if (stamp.isEmpty()) return Optional.empty();
        int p = r.pages();
        BigDecimal printing = new BigDecimal("0.89")
            .add(p >= 2 ? new BigDecimal("0.30") : BigDecimal.ZERO)
            .add(new BigDecimal("0.20").multiply(BigDecimal.valueOf(Math.max(0, Math.min(p, 20) - 2))))
            .add(new BigDecimal("0.10").multiply(BigDecimal.valueOf(Math.max(0, p - 20))))
            .add(new BigDecimal(sheets(r) > 5 ? "0.15" : "0.10"));
        boolean eu = tariffs.isInGroup("EU", r.destination());
        BigDecimal tracking = r.tracking() ? new BigDecimal(domestic ? "0.50" : eu ? "3.10" : "3.14") : BigDecimal.ZERO;
        BigDecimal total = printing.add(stamp.get()).add(tracking);
        return Optional.of(new Quote("mercifacteur", "Merci Facteur", r.tracking() ? "Lettre suivie" : "Lettre verte / internationale",
            "PRINT_AND_MAIL", total, "EUR", total, "EUR", domestic ? 3 : 4, domestic ? 5 : 15, r.tracking(),
            "Vous téléversez le document, il est imprimé et posté en France le jour même (avant 17 h). "
                + "Affranchissement estimé au tarif public La Poste ; Merci Facteur annonce parfois moins cher."
                + (r.color() ? " Surcoût couleur non précisé sur la grille publique." : ""),
            "Impression + enveloppe + affranchissement", "TTC",
            "https://www.merci-facteur.com/tarifs.php", COLLECTED));
    }

    private static final int[][] E_LETTRE = { {3, 160, 50}, {7, 375, 100}, {17, 908, 200}, {30, 1138, 400} };

    private Optional<Quote> eLettreRouge(QuoteRequest r) {
        if (!r.destination().equals("FR")) return Optional.empty();
        int s = sheets(r);
        for (int[] tier : E_LETTRE) {
            if (s > tier[0]) continue;
            int cents = tier[1] + (r.color() ? tier[2] : 0) + (r.tracking() ? 50 : 0);
            BigDecimal total = BigDecimal.valueOf(cents, 2);
            return Optional.of(new Quote("laposte-elettre-rouge", "La Poste", "e-lettre rouge", "PRINT_AND_MAIL",
                total, "EUR", total, "EUR", 1, 2, r.tracking(),
                "Lettre déposée en ligne avant 20 h, imprimée et distribuée à partir du lendemain. France uniquement.",
                "Impression + enveloppe + affranchissement", "TTC", "https://www.laposte.fr/tarifs-e-lettre-rouge", COLLECTED));
        }
        return Optional.empty();
    }
}
