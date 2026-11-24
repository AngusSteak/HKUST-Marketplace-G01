package com.hkust.marketplace.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A validated asking price on a Sale listing, always at most two decimal places.
 *
 * <p>Groupmate note: the asking price is a {@code Money}; a buyer's offer
 * ({@code PriceOffer.offeredPrice}) stays a {@link BigDecimal} because #42 already shipped that
 * shape. Convert with {@code money.amount()} at the offer dialog. Do not add {@code Money} to the
 * deal classes without asking the deal owner.
 *
 * @param amount the positive price, rounded to two decimal places
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    /** Validates and normalises the amount. */
    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Asking price is required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Asking price must be a positive amount");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Asking price can have at most two decimal places");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Parses a trimmed string, rejecting blank or malformed input. */
    public static Money of(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Asking price is required");
        }
        try {
            return new Money(new BigDecimal(raw.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid asking price: " + raw, e);
        }
    }

    /** Renders whole amounts without decimals and fractional amounts with exactly two. */
    public String getDisplay() {
        return "HK$ " + (amount.stripTrailingZeros().scale() <= 0
                ? String.format("%.0f", amount)
                : String.format("%.2f", amount));
    }

    /** Compares by amount. */
    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    /** Shows the amount with two decimals. */
    @Override
    public String toString() {
        return getDisplay();
    }
}
