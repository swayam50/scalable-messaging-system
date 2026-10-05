package io.wulfcodes.messaging.ui.controller;

import io.wulfcodes.messaging.ui.config.UiProperties;
import io.wulfcodes.messaging.ui.model.vo.SessionTokens;
import io.wulfcodes.messaging.ui.service.spec.UserSessionService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The chat page. It renders the shell; chat.js then talks to chat-service directly
 * (WebSocket + REST) with the short-lived access token it gets from /api/v1/session/token,
 * asking chat-service which node to open the WebSocket on.
 */
@Controller
@RequiredArgsConstructor
public class ChatPageController {

    private final UserSessionService userSessionService;
    private final UiProperties properties;

    @GetMapping("/")
    public String root() {
        return "redirect:/chats";
    }

    @GetMapping("/chats")
    public String chats(HttpSession session, Model model) {
        SessionTokens tokens = userSessionService.current(session).orElse(null);
        if (tokens == null) {
            return "redirect:/login";
        }
        model.addAttribute("me", tokens.user());
        model.addAttribute("chatApiUrls", String.join(",", properties.chatApiUrls()));
        return "chats";
    }
}
