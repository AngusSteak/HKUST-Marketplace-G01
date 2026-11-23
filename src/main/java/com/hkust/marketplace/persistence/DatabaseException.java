package com.hkust.marketplace.persistence;

import java.sql.SQLException;

/** An unchecked wrapper for {@link SQLException}, so stores need no checked exceptions. */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, SQLException cause) {
        super(message + ": " + cause.getMessage(), cause);
    }
}
