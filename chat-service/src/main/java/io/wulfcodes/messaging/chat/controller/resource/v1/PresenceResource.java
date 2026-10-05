package io.wulfcodes.messaging.chat.controller.resource.v1;

import io.wulfcodes.messaging.chat.config.WebConfig;
import io.wulfcodes.messaging.chat.model.dto.response.PresenceResponse;
import io.wulfcodes.messaging.chat.service.spec.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Presence snapshot for a set of users (e.g. the inbox's contacts): /api/v1/presence?userIds=a,b
 */
@RestController
@RequestMapping("/api/{version}/presence")
@RequiredArgsConstructor
public class PresenceResource {

    private static final int MAX_USERS = 200;

    private final PresenceService presenceService;

    @GetMapping(version = WebConfig.API_V1)
    public List<PresenceResponse> presence(@RequestParam List<String> userIds) {
        return presenceService.presenceOf(userIds.stream().limit(MAX_USERS).toList());
    }
}
