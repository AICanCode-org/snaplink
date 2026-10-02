package org.aicancode.snaplink.link;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void codeHasExpectedLengthAndAlphabet() {
        String code = generator.get();
        assertThat(code).hasSize(7);
        assertThat(code.chars().allMatch(c -> ShortCodeGenerator.ALPHABET.indexOf(c) >= 0)).isTrue();
    }

    @Test
    void alphabetIsBase62() {
        assertThat(ShortCodeGenerator.ALPHABET.chars().distinct().count()).isEqualTo(62);
    }

    @Test
    void codesAreRandomEnough() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            codes.add(generator.get());
        }
        assertThat(codes).hasSize(2000);
    }
}
