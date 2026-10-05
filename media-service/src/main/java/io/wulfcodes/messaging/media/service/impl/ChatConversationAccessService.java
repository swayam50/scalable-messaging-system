package io.wulfcodes.messaging.media.service.impl;

import io.wulfcodes.messaging.media.config.MediaProperties;
import io.wulfcodes.messaging.media.exception.ConversationAccessDeniedException;
import io.wulfcodes.messaging.media.exception.StorageUnavailableException;
import io.wulfcodes.messaging.media.service.spec.ConversationAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Asks chat-service whether the caller is in the conversation, RELAYING the caller's own token:
 * chat-service answers exactly as it would for that user (404 if not a participant), so this
 * service needs no copy of conversation membership and no privileged credentials.
 * Any chat node can answer; unreachable nodes are skipped.
 */
@Service
@RequiredArgsConstructor
public class ChatConversationAccessService implements ConversationAccessService {

    private final RestClient chatRestClient;
    private final MediaProperties properties;

    @Override
    public void requireParticipant(String conversationId, String bearerToken) {
        for (String baseUrl : properties.chatApiUrls()) {
            try {
                chatRestClient.get()
                        .uri(baseUrl + "/api/v1/conversations/{id}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException e) {
                throw new ConversationAccessDeniedException(conversationId);   // 401/403/404: not a participant
            } catch (ResourceAccessException e) {
                // node unreachable: try the next one
            }
        }
        throw new StorageUnavailableException("Chat service unreachable for membership check");
    }
}
