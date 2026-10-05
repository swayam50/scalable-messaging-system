package io.wulfcodes.messaging.chat.model.vo;

/**
 * Types of JSON frames exchanged over the chat WebSocket.
 */
public enum FrameType {
    // ---- client -> server
    /** send a message */
    SEND,
    /** I have read the conversation up to messageId */
    READ,
    /** I started/stopped typing (also sent server -> client to the peer) */
    TYPING,

    // ---- server -> client
    /** your message was stored; here is its server id */
    ACK,
    /** a new message */
    MESSAGE,
    /** a message was delivered to / read by the other participant */
    RECEIPT,
    /** a contact came online / went offline */
    PRESENCE,
    /** the frame could not be processed */
    ERROR
}
