package com.postcompare;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class QuoteController {
    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());
    private final List<MailProvider> providers = List.of(
        new DemoProvider("local", "Atelier local", true, 105, 2, true),
        new DemoProvider("global", "Courrier international", true, 80, 5, false),
        new DemoProvider("postal", "Poste au départ", false, 130, 3, true),
        new DemoProvider("express", "Atelier express", true, 295, 1, true));

    @GetMapping("/countries") public List<Map<String, String>> countries() {
        return COUNTRIES.stream().map(code -> Map.of("code", code, "name",
            new Locale("", code).getDisplayCountry(Locale.FRENCH)))
            .sorted(Comparator.comparing(c -> c.get("name"))).toList();
    }

    @PostMapping("/quotes") public QuoteResponse quotes(@Valid @RequestBody QuoteRequest request) {
        if (!COUNTRIES.contains(request.origin()) || !COUNTRIES.contains(request.destination()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pays inconnu");
        List<Quote> quotes = providers.stream().map(p -> p.quote(request)).flatMap(Optional::stream)
            .sorted(Comparator.comparing(Quote::price).thenComparing(Quote::id)).toList();
        return new QuoteResponse("DEMO", "Tarifs et délais fictifs. Aucun prestataire réel interrogé.", quotes);
    }

    public record QuoteResponse(String mode, String notice, List<Quote> quotes) {}
}
