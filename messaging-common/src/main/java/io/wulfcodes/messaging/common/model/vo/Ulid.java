package io.wulfcodes.messaging.common.model.vo;

import java.time.Instant;
import java.util.Arrays;

/**
 * Immutable ULID value object (Universally Unique Lexicographically Sortable Identifier).
 *
 * <pre>
 *  128 bits = 48-bit timestamp (ms since Unix epoch) | 80 bits of randomness
 *  msb      = [ 48-bit timestamp | top 16 random bits ]
 *  lsb      = [ low 64 random bits ]
 * </pre>
 *
 * Its string form is 26 characters of Crockford Base32. The first 10 characters encode the
 * timestamp, so sorting the strings sorts by time. 80 random bits make it unguessable,
 * which is why it is used for public ids (users, conversations).
 */
public record Ulid(long msb, long lsb) implements Comparable<Ulid> {

    public static final int LENGTH = 26;
    public static final long MAX_TIMESTAMP = (1L << 48) - 1;

    /** Crockford Base32: digits + letters without I, L, O, U (avoids misreading). */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final byte[] DECODE = new byte[128];

    static {
        Arrays.fill(DECODE, (byte) -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            DECODE[ALPHABET[i]] = (byte) i;
            DECODE[Character.toLowerCase(ALPHABET[i])] = (byte) i;
        }
    }

    public static Ulid of(long timestamp, long randomHigh16, long randomLow64) {
        if (timestamp < 0 || timestamp > MAX_TIMESTAMP) {
            throw new IllegalArgumentException("ULID timestamp must fit in 48 bits: " + timestamp);
        }
        return new Ulid((timestamp << 16) | (randomHigh16 & 0xFFFFL), randomLow64);
    }

    public long timestamp() {
        return msb >>> 16;
    }

    public Instant instant() {
        return Instant.ofEpochMilli(timestamp());
    }

    /**
     * Returns the next ULID in the same millisecond (random part + 1), used for monotonic generation.
     */
    public Ulid increment() {
        long newLsb = lsb + 1;
        long newMsb = msb;
        if (newLsb == 0) {                       // carry from low 64 bits into the top 16 random bits
            long high16 = (msb & 0xFFFFL) + 1;
            if (high16 > 0xFFFFL) {
                throw new IllegalStateException("ULID random component overflow within the same millisecond");
            }
            newMsb = (msb & ~0xFFFFL) | high16;
        }
        return new Ulid(newMsb, newLsb);
    }

    /**
     * Encodes 128 bits as 26 base32 characters. 26 * 5 = 130 bits, so the value is treated as
     * left-padded with 2 zero bits; that is why the first character is always 0-7.
     */
    @Override
    public String toString() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            int shift = 125 - 5 * i;              // bit position of this 5-bit group
            chars[i] = ALPHABET[fiveBitsAt(shift)];
        }
        return new String(chars);
    }

    private int fiveBitsAt(int shift) {
        if (shift >= 64) {
            return (int) ((msb >>> (shift - 64)) & 0x1F);
        }
        if (shift <= 59) {
            return (int) ((lsb >>> shift) & 0x1F);
        }
        // the 5-bit group straddles lsb and msb (shift 60..63)
        return (int) (((lsb >>> shift) | (msb << (64 - shift))) & 0x1F);
    }

    public static Ulid parse(String value) {
        if (value == null || value.length() != LENGTH) {
            throw new IllegalArgumentException("ULID must be " + LENGTH + " characters: " + value);
        }
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < LENGTH; i++) {
            char c = value.charAt(i);
            int v = c < 128 ? DECODE[c] : -1;
            if (v < 0) {
                throw new IllegalArgumentException("Invalid ULID character '" + c + "' in " + value);
            }
            if (i == 0 && v > 7) {
                throw new IllegalArgumentException("ULID overflows 128 bits (first char must be 0-7): " + value);
            }
            int shift = 125 - 5 * i;
            if (shift >= 64) {
                msb |= (long) v << (shift - 64);
            } else if (shift <= 59) {
                lsb |= (long) v << shift;
            } else {
                lsb |= (long) v << shift;              // low bits land in lsb
                msb |= (long) v >>> (64 - shift);       // high bits carry into msb
            }
        }
        return new Ulid(msb, lsb);
    }

    @Override
    public int compareTo(Ulid other) {
        int cmp = Long.compareUnsigned(msb, other.msb);
        return cmp != 0 ? cmp : Long.compareUnsigned(lsb, other.lsb);
    }
}
