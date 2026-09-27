package com.postcompare;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TariffCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode data() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/tariffs.json")) {
            return (ObjectNode) mapper.readTree(in);
        }
    }
    private TariffCatalog load(ObjectNode data) throws Exception {
        return new TariffCatalog(mapper.treeToValue(data, Tariffs.Catalog.class));
    }
    @Test void cyclicGroupsAreRejected() throws Exception {
        ObjectNode data = data();
        ObjectNode groups = (ObjectNode) data.get("groups");
        groups.putArray("A").add("@B");
        groups.putArray("B").add("@A");
        assertThrows(IllegalStateException.class, () -> load(data));
    }
    @Test void unknownZoneCountryIsRejected() throws Exception {
        ObjectNode data = data();
        ((ObjectNode) data.at("/carriers/0/services/0/zones/0")).putArray("countries").add("ZZ");
        assertThrows(IllegalStateException.class, () -> load(data));
    }
    @Test void nonPositiveExchangeRateIsRejected() throws Exception {
        ObjectNode data = data();
        ((ObjectNode) data.at("/fx/perEuro")).put("GBP", -1);
        assertThrows(IllegalStateException.class, () -> load(data));
    }
    @Test void partialColorPriceIsRejected() throws Exception {
        ObjectNode data = data();
        for (var carrier : data.get("carriers")) {
            if (!carrier.get("type").asText().equals("ONLINE")) continue;
            ((ObjectNode) carrier.at("/services/0/zones/0")).remove("colorExtra");
            break;
        }
        assertThrows(IllegalStateException.class, () -> load(data));
    }
}
