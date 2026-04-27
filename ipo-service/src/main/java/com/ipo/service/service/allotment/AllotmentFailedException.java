package com.ipo.service.service.allotment;

/**
 * AllotmentFailedException
 *
 * Thrown when allotment process fails.
 */
public class AllotmentFailedException extends RuntimeException {

    public AllotmentFailedException(String message) {
        super(message);
    }

    public AllotmentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
