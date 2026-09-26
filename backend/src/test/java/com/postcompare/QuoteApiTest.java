package com.postcompare;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc
class QuoteApiTest {
    @Autowired MockMvc mvc;

    private ResultActions quote(String origin, String destination, int pages, int weight, boolean tracking) throws Exception {
        String body = """
            {"origin":"%s","destination":"%s","pages":%d,"weight":%d,"color":false,"duplex":true,"tracking":%s}
            """.formatted(origin, destination, pages, weight, tracking);
        return mvc.perform(post("/api/quotes").contentType("application/json").content(body));
    }

    @Test void franceDomesticUsesLaPosteGrid() throws Exception {
        quote("FR", "FR", 2, 20, false).andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("PUBLIC_RATES"))
            .andExpect(jsonPath("$.quotes[?(@.id=='laposte-lettre-verte')].price").value(contains(1.52)))
            .andExpect(jsonPath("$.quotes[?(@.id=='laposte-elettre-rouge')].price").value(contains(1.60)));
    }

    @Test void weightBracketsAreUpperBounds() throws Exception {
        quote("FR", "FR", 2, 21, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='laposte-lettre-verte')].price").value(contains(3.10)));
        quote("FR", "FR", 2, 2001, false).andExpect(status().isBadRequest());
    }

    @Test void franceToRomaniaComparesPostExpressAndOnline() throws Exception {
        quote("FR", "RO", 2, 20, false).andExpect(status().isOk())
            .andExpect(jsonPath("$.quotes[*].id", hasItems("laposte-internationale", "mercifacteur")))
            .andExpect(jsonPath("$.quotes[*].id", not(hasItem("laposte-lettre-verte"))));
        quote("FR", "RO", 2, 20, true)
            .andExpect(jsonPath("$.quotes[*].id", hasItems("laposte-suivie-internationale", "dhl-express-worldwide")))
            .andExpect(jsonPath("$.quotes[*].tracking", everyItem(is(true))));
    }

    @Test void foreignCurrencyIsConvertedAndOriginalKept() throws Exception {
        quote("GB", "GB", 1, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='royalmail-second-class')].originalPrice").value(contains(0.91)))
            .andExpect(jsonPath("$.quotes[?(@.id=='royalmail-second-class')].originalCurrency").value(contains("GBP")))
            .andExpect(jsonPath("$.quotes[?(@.id=='royalmail-second-class')].price").value(contains(1.06)));
    }

    @Test void zonesPickTheRightGrid() throws Exception {
        quote("IT", "AU", 1, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='posteitaliane-postamail-internazionale')].price").value(contains(3.35)));
        quote("IT", "DE", 1, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='posteitaliane-postamail-internazionale')].price").value(contains(1.35)));
    }

    @Test void uncoveredOriginStillGetsOnlineServices() throws Exception {
        quote("BR", "FR", 3, 20, false).andExpect(status().isOk())
            .andExpect(jsonPath("$.quotes[*].method", everyItem(is("PRINT_AND_MAIL"))))
            .andExpect(jsonPath("$.quotes[*].id", hasItems("mercifacteur", "laposte-elettre-rouge", "postaonline-posta-ordinaria-online-int")));
    }

    @Test void localOnlineServicesArePricedPerPageOrSheet() throws Exception {
        // Web Letter: 129 JPY + 6 JPY per extra page, 3 pages = 141 JPY
        quote("FR", "JP", 3, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='japanpost-webletter-webletter')].originalPrice").value(contains(141)));
        // Postaonline: 1.32 + 0.06 per extra sheet, 4 pages recto verso = 2 sheets
        quote("DE", "IT", 4, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='postaonline-posta-ordinaria-online')].price").value(contains(1.38)));
    }

    @Test void worldwidePostsUseTheirZones() throws Exception {
        quote("KR", "FR", 1, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='koreapost-airmail')].originalPrice").value(contains(780)));
        quote("AR", "UY", 1, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='correoargentino-carta-simple-int')].originalPrice").value(contains(8600)));
        quote("BR", "BR", 1, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='correios-br-carta')].originalCurrency").value(contains("BRL")));
    }

    @Test void rejectsInvalidInput() throws Exception {
        quote("ZZ", "FR", 2, 20, false).andExpect(status().isBadRequest());
        quote("FR", "FR", 0, 20, false).andExpect(status().isBadRequest());
        mvc.perform(post("/api/quotes").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/quotes").contentType("application/json")
            .content("{\"origin\":\"FR\",\"destination\":\"RO\",\"pages\":2.5,\"weight\":20,\"color\":false,\"duplex\":true,\"tracking\":false}"))
            .andExpect(status().isBadRequest());
    }

    @Test void listsCarriersWithSources() throws Exception {
        mvc.perform(get("/api/carriers")).andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(40))))
            .andExpect(jsonPath("$[*].source", everyItem(startsWith("https://"))));
    }

    @Test void servesPackagedFrontend() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("PostCompare")));
    }

    @Test void noTrackingRequirementDoesNotHideTrackedServices() throws Exception {
        quote("FR", "RO", 2, 20, false)
            .andExpect(jsonPath("$.quotes[*].id", hasItem("dhl-express-worldwide")));
    }

    @Test void unknownColorPricesAreNotReturned() throws Exception {
        mvc.perform(post("/api/quotes").contentType("application/json").content("""
            {"origin":"FR","destination":"FR","pages":2,"weight":20,"color":true,"duplex":true,"tracking":false}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quotes[*].id", not(hasItem("mercifacteur"))))
            .andExpect(jsonPath("$.quotes[*].id", not(hasItem("postaonline-posta-ordinaria-online-int"))))
            .andExpect(jsonPath("$.quotes[?(@.id=='laposte-elettre-rouge')].price").value(contains(2.10)));
    }

    @Test void letterStreamDoesNotUnderquoteMultiPageMail() throws Exception {
        quote("FR", "US", 2, 20, false)
            .andExpect(jsonPath("$.quotes[*].id", not(hasItem("letterstream-first-class"))));
        quote("FR", "US", 1, 20, false)
            .andExpect(jsonPath("$.quotes[?(@.id=='letterstream-first-class')].originalPrice").value(contains(1.27)));
    }
}
