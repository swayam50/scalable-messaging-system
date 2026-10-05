package io.wulfcodes.messaging.common.model.vo;

/**
 * What a chat message carries. Everything except TEXT has an attachment stored by media-service.
 */
public enum ContentType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE;

    public boolean isMedia() {
        return this != TEXT;
    }
}
