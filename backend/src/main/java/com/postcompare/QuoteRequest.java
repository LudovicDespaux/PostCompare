package com.postcompare;

import jakarta.validation.constraints.*;

public record QuoteRequest(
    @NotBlank @Pattern(regexp="[A-Z]{2}") String origin,
    @NotBlank @Pattern(regexp="[A-Z]{2}") String destination,
    @NotNull @Min(1) @Max(50) Integer pages,
    @NotNull @Min(1) @Max(2000) Integer weight,
    @NotNull Boolean color,
    @NotNull Boolean duplex,
    @NotNull Boolean tracking
) {}
