package com.postcompare;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest @AutoConfigureMockMvc
class QuoteApiTest {
    @Autowired MockMvc mvc;
    private String body(String origin, int pages, boolean tracking) {
        return """
            {"origin":"%s","destination":"RO","pages":%d,"weight":20,
             "color":false,"duplex":true,"tracking":%s}
            """.formatted(origin, pages, tracking);
    }
    @Test void returnsSortedDemoQuotes() throws Exception {
        mvc.perform(post("/api/quotes").contentType("application/json").content(body("FR",2,false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("DEMO"))
            .andExpect(jsonPath("$.quotes", hasSize(4)))
            .andExpect(jsonPath("$.quotes[0].id").value("global"))
            .andExpect(jsonPath("$.quotes[0].price").value(1.64));
    }
    @Test void trackingExcludesUnsupportedProvider() throws Exception {
        mvc.perform(post("/api/quotes").contentType("application/json").content(body("FR",2,true)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quotes",hasSize(3)))
            .andExpect(jsonPath("$.quotes[*].tracking", everyItem(is(true))));
    }
    @Test void rejectsUnknownCountryAndInvalidPages() throws Exception {
        mvc.perform(post("/api/quotes").contentType("application/json").content(body("ZZ",2,false)))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/quotes").contentType("application/json").content(body("FR",0,false)))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/quotes").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }
    @Test void rejectsFractionalPagesRatherThanSilentlyTruncating() throws Exception {
        mvc.perform(post("/api/quotes").contentType("application/json")
            .content(body("FR",2,false).replace("\"pages\":2", "\"pages\":2.5")))
            .andExpect(status().isBadRequest());
    }
    @Test void servesPackagedFrontend() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("PostCompare")));
    }
}
