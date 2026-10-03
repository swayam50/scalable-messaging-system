package io.wulfcodes.messaging.common.util;

/**
 * Generates globally unique, time-ordered 64-bit identifiers.
 */
@FunctionalInterface
public interface IdGenerator {

    long nextId();
}
