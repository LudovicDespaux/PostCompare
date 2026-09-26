package com.postcompare;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.postcompare.Tariffs.*;

/** Loads the tariff file once and answers "what does this service cost for this letter?". */
@Component
public class TariffCatalog {
    private final Catalog catalog;
    private final Map<String, Set<String>> groups = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();

    public TariffCatalog() throws IOException {
        this(load());
    }

    private static Catalog load() throws IOException {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try (InputStream in = new ClassPathResource("tariffs.json").getInputStream()) {
            return mapper.readValue(in, Catalog.class);
        }
    }

    TariffCatalog(Catalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
        catalog.groups().keySet().forEach(this::expand);
        validate();
    }

    public Catalog catalog() { return catalog; }

    public Optional<Carrier> carrier(String id) {
        return catalog.carriers().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** Resolves "@GROUP" references recursively. */
    private Set<String> expand(String group) {
        Set<String> cached = groups.get(group);
        if (cached != null) return cached;
        List<String> members = catalog.groups().get(group);
        if (members == null) throw new IllegalStateException("Groupe inconnu : " + group);
        Set<String> out = new HashSet<>();
        if (!resolving.add(group)) throw new IllegalStateException("Cycle de groupes : " + group);
        for (String m : members) out.addAll(m.startsWith("@") ? expand(m.substring(1)) : Set.of(m));
        resolving.remove(group);
        Set<String> result = Set.copyOf(out);
        groups.put(group, result);
        return result;
    }

    public boolean isInGroup(String group, String country) { return expand(group).contains(country); }

    private boolean in(List<String> list, String country) {
        if (list == null) return false;
        for (String e : list) {
            if (e.equals("*") || e.equals(country)) return true;
            if (e.startsWith("@") && expand(e.substring(1)).contains(country)) return true;
        }
        return false;
    }

    public Optional<Zone> zoneFor(Service service, String destination) {
        return service.zones().stream()
            .filter(z -> in(z.countries(), destination) && !in(z.exclude(), destination)).findFirst();
    }

    /** Price of the first bracket whose upper weight bound is >= weight, if any. */
    public static Optional<BigDecimal> priceFor(Zone zone, int grams) {
        return zone.rates().stream().filter(r -> r.get(0).intValue() >= grams).map(r -> r.get(1)).findFirst();
    }

    public BigDecimal toEuro(BigDecimal amount, String currency) {
        BigDecimal rate = catalog.fx().perEuro().get(currency);
        if (rate == null) throw new IllegalStateException("Devise sans taux : " + currency);
        return amount.divide(rate, 2, RoundingMode.HALF_UP);
    }

    private void validate() {
        Set<String> iso = Set.of(Locale.getISOCountries());
        LocalDate.parse(catalog.collectedOn());
        LocalDate.parse(catalog.fx().date());
        catalog.fx().perEuro().forEach((code, rate) -> {
            Currency.getInstance(code);
            if (rate == null || rate.signum() <= 0) throw new IllegalStateException("Taux invalide : " + code);
        });
        if (BigDecimal.ONE.compareTo(catalog.fx().perEuro().getOrDefault("EUR", BigDecimal.ZERO)) != 0)
            throw new IllegalStateException("Le taux EUR doit valoir 1");
        groups.forEach((g, members) -> members.forEach(c -> {
            if (!iso.contains(c)) throw new IllegalStateException("Code pays invalide " + c + " dans " + g);
        }));
        Set<String> ids = new HashSet<>();
        for (Carrier c : catalog.carriers()) {
            if (!ids.add(c.id())) throw new IllegalStateException("Identifiant en double : " + c.id());
            if (!Set.of("POSTAL", "EXPRESS", "ONLINE").contains(c.type()))
                throw new IllegalStateException("Type invalide : " + c.id());
            LocalDate.parse(c.validFrom());
            URI source = URI.create(c.source());
            if (!"https".equals(source.getScheme()) || source.getHost() == null)
                throw new IllegalStateException("Source HTTPS requise : " + c.id());
            validateCountries(c.origins(), iso, c.id());
            toEuro(BigDecimal.ONE, c.currency());
            if (c.online() && (c.postsFrom() == null || !iso.contains(c.postsFrom())))
                throw new IllegalStateException("postsFrom manquant : " + c.id());
            Set<String> services = new HashSet<>();
            for (Service s : c.services()) {
                String where = c.id() + "/" + s.id();
                if (!services.add(s.id())) throw new IllegalStateException("Service en double : " + where);
                if (!Set.of("DOMESTIC", "INTERNATIONAL").contains(s.scope()))
                    throw new IllegalStateException("Portée invalide : " + where);
                if (c.online() && (!Set.of("PAGE", "SHEET").contains(s.unit()) || s.included() == null || s.max() == null))
                    throw new IllegalStateException("Service en ligne incomplet : " + where);
                if (c.online() && (s.included() < 1 || s.max() < s.included()))
                    throw new IllegalStateException("Limites invalides : " + where);
                for (Zone z : s.zones()) {
                    validateCountries(z.countries(), iso, where);
                    if (z.exclude() != null) validateCountries(z.exclude(), iso, where);
                    if ((z.minDays() == null ? s.minDays() : z.minDays()) == null
                        || (z.maxDays() == null ? s.maxDays() : z.maxDays()) == null)
                        throw new IllegalStateException("Délai manquant : " + where + "/" + z.name());
                    int min = z.minDays() == null ? s.minDays() : z.minDays();
                    int max = z.maxDays() == null ? s.maxDays() : z.maxDays();
                    if (min < 0 || max < min) throw new IllegalStateException("Délai invalide : " + where);
                    if (c.online()) {
                        if (z.base() == null || z.extra() == null)
                            throw new IllegalStateException("Prix manquant : " + where + "/" + z.name());
                        if (z.base().signum() < 0 || z.extra().signum() < 0
                            || (z.colorBase() == null) != (z.colorExtra() == null)
                            || (z.colorBase() != null && (z.colorBase().signum() < 0 || z.colorExtra().signum() < 0)))
                            throw new IllegalStateException("Prix en ligne invalide : " + where);
                        continue;
                    }
                    if (z.rates() == null || z.rates().isEmpty())
                        throw new IllegalStateException("Tranches manquantes : " + where + "/" + z.name());
                    int last = 0;
                    for (List<BigDecimal> r : z.rates()) {
                        if (r.size() != 2 || r.get(0).intValueExact() <= last || r.get(1).signum() < 0)
                            throw new IllegalStateException("Tranches non croissantes : " + where + "/" + z.name());
                        last = r.get(0).intValue();
                    }
                }
            }
        }
    }

    private void validateCountries(List<String> codes, Set<String> iso, String where) {
        if (codes == null) throw new IllegalStateException("Pays manquants : " + where);
        for (String code : codes) {
            if (code.startsWith("@")) expand(code.substring(1));
            else if (!code.equals("*") && !iso.contains(code))
                throw new IllegalStateException("Code pays invalide : " + code + " dans " + where);
        }
    }
}
