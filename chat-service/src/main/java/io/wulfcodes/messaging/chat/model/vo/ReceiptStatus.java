package io.wulfcodes.messaging.chat.model.vo;

/** Delivery state of a message, as shown by the sender's ticks. */
public enum ReceiptStatus {
    /** pushed to at least one of the recipient's live sessions */
    DELIVERED,
    /** the recipient opened the conversation up to this message */
    READ
}
