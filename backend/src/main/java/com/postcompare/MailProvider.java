package com.postcompare;

import java.util.List;

/** Returns normalized quotes for a letter, or an empty list if the route is not covered. */
public interface MailProvider {
    List<Quote> quotes(QuoteRequest request);
}
