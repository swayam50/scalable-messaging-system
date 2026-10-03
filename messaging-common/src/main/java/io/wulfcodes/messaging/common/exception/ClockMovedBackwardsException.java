package io.wulfcodes.messaging.common.exception;

/**
 * Thrown when the system clock goes backwards (e.g. an NTP correction).
 * Generating an ID at that point could duplicate one already issued, so we refuse.
 */
public class ClockMovedBackwardsException extends RuntimeException {

    public ClockMovedBackwardsException(long lastTimestamp, long currentTimestamp) {
        super("Clock moved backwards by %d ms (last=%d, now=%d); refusing to generate id"
                .formatted(lastTimestamp - currentTimestamp, lastTimestamp, currentTimestamp));
    }
}
