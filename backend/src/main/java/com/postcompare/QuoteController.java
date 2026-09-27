package com.postcompare;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class QuoteController {
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
    private final List<MailProvider> providers;
    private final TariffCatalog tariffs;

    public QuoteController(List<MailProvider> providers, TariffCatalog tariffs) {
        this.providers = providers; this.tariffs = tariffs;
    }

    @GetMapping("/countries") public List<Map<String, Object>> countries() {
        Set<String> covered = new HashSet<>();
        tariffs.catalog().carriers().stream().filter(c -> !c.online()).forEach(c -> covered.addAll(c.origins()));
        return COUNTRIES.stream().map(code -> Map.<String, Object>of("code", code, "name",
            new Locale("", code).getDisplayCountry(Locale.FRENCH), "postalRates", covered.contains(code)))
            .sorted(Comparator.comparing(c -> (String) c.get("name"))).toList();
    }

    /** Which carriers are covered, with sources: useful for a "nos sources" page. */
    @GetMapping("/carriers") public List<Map<String, Object>> carriers() {
        return tariffs.catalog().carriers().stream().map(c -> Map.<String, Object>of("id", c.id(), "name", c.name(),
            "type", c.type(), "origins", c.online() ? List.of(c.postsFrom()) : c.origins(),
            "source", c.source(), "validFrom", c.validFrom())).toList();
    }

    @PostMapping("/quotes") public QuoteResponse quotes(@Valid @RequestBody QuoteRequest request) {
        if (!COUNTRIES.contains(request.origin()) || !COUNTRIES.contains(request.destination()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pays inconnu");
        List<Quote> quotes = providers.stream().flatMap(p -> p.quotes(request).stream())
            .sorted(Comparator.comparing(Quote::price).thenComparing(Quote::id)).toList();
        var cat = tariffs.catalog();
        long postal = cat.carriers().stream().filter(c -> !c.online()).count();
        long online = cat.carriers().size() - postal + 2; // + Merci Facteur, e-lettre rouge
        return new QuoteResponse("PUBLIC_RATES",
            postal + " opérateurs postaux et express, " + online + " services d’envoi en ligne. Catalogue relevé le " + fr(cat.collectedOn()) + ". Conversion avec les taux de référence du " + fr(cat.fx().date())
                + " (BCE et source complémentaire pour TWD/ARS). Montants estimatifs, taxes et suppléments variables. Vérifiez auprès du prestataire avant envoi.", quotes);
    }

    private static String fr(String isoDate) {
        return LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH));
    }

    public record QuoteResponse(String mode, String notice, List<Quote> quotes) {}
}
