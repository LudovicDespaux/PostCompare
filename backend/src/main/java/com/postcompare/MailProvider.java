package com.postcompare;

import java.util.Optional;

/** A real adapter must return a normalized total, or no quote if the route is unsupported. */
public interface MailProvider {
    Optional<Quote> quote(QuoteRequest request);
}
