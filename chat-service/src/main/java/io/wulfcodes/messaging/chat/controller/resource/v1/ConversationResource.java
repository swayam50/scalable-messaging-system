package io.wulfcodes.messaging.chat.controller.resource.v1;

import io.wulfcodes.messaging.chat.config.WebConfig;
import io.wulfcodes.messaging.chat.model.dto.request.CreateConversationRequest;
import io.wulfcodes.messaging.chat.model.dto.response.ConversationResponse;
import io.wulfcodes.messaging.chat.model.dto.response.InboxEntryResponse;
import io.wulfcodes.messaging.chat.model.dto.response.MessagePageResponse;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conversations API v1: /api/v1/conversations. Sending happens over the WebSocket;
 * REST covers starting a chat, the inbox, and paging through history.
 */
@RestController
@RequestMapping("/api/{version}/conversations")
@RequiredArgsConstructor
public class ConversationResource {

    private final ConversationService conversationService;
    private final MessageService messageService;

    @GetMapping(version = WebConfig.API_V1)
    public List<InboxEntryResponse> inbox(@AuthenticationPrincipal Jwt jwt) {
        return conversationService.getInbox(jwt.getSubject());
    }

    /** Idempotent: returns the existing conversation with this peer, or creates it. */
    @PostMapping(version = WebConfig.API_V1)
    public ConversationResponse getOrCreate(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody CreateConversationRequest request) {
        return conversationService.getOrCreateDirect(jwt.getSubject(), request.peerId());
    }

    /** 404 unless the caller is a participant (media-service relies on this for access checks). */
    @GetMapping(path = "/{conversationId}", version = WebConfig.API_V1)
    public ConversationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String conversationId) {
        return conversationService.getForParticipant(conversationId, jwt.getSubject());
    }

    @GetMapping(path = "/{conversationId}/messages", version = WebConfig.API_V1)
    public MessagePageResponse history(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable String conversationId,
                                       @RequestParam(required = false) String before,
                                       @RequestParam(defaultValue = "50") int limit) {
        return messageService.history(jwt.getSubject(), conversationId, before, limit);
    }
}
