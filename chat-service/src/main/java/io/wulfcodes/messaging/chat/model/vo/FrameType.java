package io.wulfcodes.messaging.chat.model.vo;

/**
 * Types of JSON frames exchanged over the chat WebSocket.
 */
public enum FrameType {
    /** client -> server: send a message */
    SEND,
    /** server -> sender: message stored, here is its server id */
    ACK,
    /** server -> participant: a new message */
    MESSAGE,
    /** server -> client: the frame could not be processed */
    ERROR
}
