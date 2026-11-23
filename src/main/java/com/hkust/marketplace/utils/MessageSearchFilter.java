package com.hkust.marketplace.utils;

/**
 * Which kinds of {@link com.hkust.marketplace.models.Message} a chat search returns:
 * every message, only free text, or only Formal Offer cards. Used by
 * {@link ChatService#search}.
 */
public enum MessageSearchFilter {
    /** Both free-text messages and Formal Offers. */
    ALL,
    /** Only free-text messages; Formal Offers are excluded. */
    TEXT_ONLY,
    /** Only Formal Offer cards; free-text messages are excluded. */
    OFFERS_ONLY
}
