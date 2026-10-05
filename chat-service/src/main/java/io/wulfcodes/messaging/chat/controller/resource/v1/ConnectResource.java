package io.wulfcodes.messaging.chat.controller.resource.v1;

import io.wulfcodes.messaging.chat.config.WebConfig;
import io.wulfcodes.messaging.chat.model.dto.response.ConnectResponse;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the client which node owns it on the consistent-hash ring; the client opens its
 * WebSocket there. Any node can answer: every node computes the same ring.
 */
@RestController
@RequestMapping("/api/{version}/connect")
@RequiredArgsConstructor
public class ConnectResource {

    private final RingService ringService;

    @GetMapping(version = WebConfig.API_V1)
    public ConnectResponse connect(@AuthenticationPrincipal Jwt jwt) {
        NodeInfo owner = ringService.ownerOf(jwt.getSubject());
        return new ConnectResponse(owner.nodeId(), owner.wsUrl());
    }
}
