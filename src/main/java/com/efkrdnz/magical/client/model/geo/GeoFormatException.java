package com.efkrdnz.magical.client.model.geo;

/** A geometry file the baker cannot use, with the file and the fix in the message. */
public final class GeoFormatException extends RuntimeException {
    public GeoFormatException(String message) {
        super(message);
    }
}
