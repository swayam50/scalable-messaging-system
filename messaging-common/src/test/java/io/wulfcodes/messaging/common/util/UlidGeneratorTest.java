package io.wulfcodes.messaging.common.util;

import io.wulfcodes.messaging.common.model.vo.Ulid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UlidGeneratorTest {

    private static final long T0 = 1_780_000_000_000L;

    @Test
    void stringFormIs26CrockfordCharacters() {
        String ulid = new UlidGenerator().nextString();

        assertThat(ulid).hasSize(26).matches("[0-7][0-9A-HJKMNP-TV-Z]{25}");
    }

    @Test
    void parseRoundTrips() {
        UlidGenerator generator = new UlidGenerator();
        for (int i = 0; i < 10_000; i++) {
            Ulid ulid = generator.next();
            assertThat(Ulid.parse(ulid.toString())).isEqualTo(ulid);
        }
    }

    @Test
    void timestampComesFromTheClock() {
        UlidGenerator generator = new UlidGenerator(new MutableClock(T0), new Random(1));

        Ulid ulid = generator.next();

        assertThat(ulid.timestamp()).isEqualTo(T0);
        assertThat(Ulid.parse(ulid.toString()).timestamp()).isEqualTo(T0);
    }

    @Test
    void idsInTheSameMillisecondAreMonotonic() {
        UlidGenerator generator = new UlidGenerator(new MutableClock(T0), new Random(2));
        Ulid previous = generator.next();
        for (int i = 0; i < 1_000; i++) {
            Ulid next = generator.next();
            assertThat(next).isGreaterThan(previous);
            assertThat(next.toString()).isGreaterThan(previous.toString());
            assertThat(next.timestamp()).isEqualTo(T0);
            previous = next;
        }
    }

    @Test
    void stringOrderMatchesTimeOrder() {
        MutableClock clock = new MutableClock(T0);
        UlidGenerator generator = new UlidGenerator(clock, new Random(3));
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            clock.set(T0 + i * 7L);
            ids.add(generator.nextString());
        }

        assertThat(ids).isSorted();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "01ARZ3NDEKTSV4RRFFQ69G5FA",     // 25 chars
            "01ARZ3NDEKTSV4RRFFQ69G5FAVX",   // 27 chars
            "01ARZ3NDEKTSV4RRFFQ69G5FAI",    // 'I' is not in Crockford base32
            "81ARZ3NDEKTSV4RRFFQ69G5FAV"     // first char > 7 overflows 128 bits
    })
    void invalidStringsAreRejected(String value) {
        assertThatThrownBy(() -> Ulid.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
