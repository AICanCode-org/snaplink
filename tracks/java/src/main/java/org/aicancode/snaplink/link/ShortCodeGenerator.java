package org.aicancode.snaplink.link;

import java.util.function.Supplier;

/** Short-code generation: random Base62 from a cryptographically secure source. */
public final class ShortCodeGenerator implements Supplier<String> {

    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final int CODE_LENGTH = 7;

    @Override
    public String get() {
        // TODO(phase-3 M1): return CODE_LENGTH characters picked from ALPHABET using a java.security.SecureRandom
        // field (cryptographically secure), NOT java.util.Random. Why? See ADR-0001.
        throw new UnsupportedOperationException("TODO(phase-3 M1): ShortCodeGenerator.get");
    }
}
