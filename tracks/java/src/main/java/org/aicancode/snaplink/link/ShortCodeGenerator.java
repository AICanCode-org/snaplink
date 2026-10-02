package org.aicancode.snaplink.link;

import java.security.SecureRandom;
import java.util.function.Supplier;

/** Short-code generation: random Base62 from a cryptographically secure source. */
public final class ShortCodeGenerator implements Supplier<String> {

    public static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final int CODE_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String get() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
