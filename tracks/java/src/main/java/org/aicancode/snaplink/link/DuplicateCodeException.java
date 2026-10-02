package org.aicancode.snaplink.link;

/** Thrown by a repository when the unique constraint on links.code is violated. */
public class DuplicateCodeException extends RuntimeException {

    public DuplicateCodeException(String code) {
        super("duplicate code " + code);
    }
}
