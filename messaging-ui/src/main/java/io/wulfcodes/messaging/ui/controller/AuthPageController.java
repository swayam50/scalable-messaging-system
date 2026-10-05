package io.wulfcodes.messaging.ui.controller;

import io.wulfcodes.messaging.common.model.dto.request.LoginRequest;
import io.wulfcodes.messaging.common.model.dto.request.RegisterRequest;
import io.wulfcodes.messaging.ui.exception.AuthClientException;
import io.wulfcodes.messaging.ui.service.spec.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Login / register / logout pages (server-rendered Mustache).
 * The shared request records from messaging-common double as form-backing objects.
 */
@Controller
@RequiredArgsConstructor
public class AuthPageController {

    private final UserSessionService userSessionService;

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        return userSessionService.current(session).isPresent() ? "redirect:/chats" : "login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginRequest form, BindingResult binding,
                        HttpServletRequest request, Model model) {
        if (binding.hasErrors()) {
            return withError(model, "login", "Please enter your username/email and password", form.login());
        }
        try {
            userSessionService.login(form, newSession(request));
            return "redirect:/chats";
        } catch (AuthClientException e) {
            return withError(model, "login", e.getMessage(), form.login());
        }
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest form, BindingResult binding,
                           HttpServletRequest request, Model model) {
        if (binding.hasErrors()) {
            var error = binding.getFieldErrors().getFirst();
            model.addAttribute("form", form);
            return withError(model, "register", error.getField() + " " + error.getDefaultMessage(), null);
        }
        try {
            userSessionService.register(form, newSession(request));
            return "redirect:/chats";
        } catch (AuthClientException e) {
            model.addAttribute("form", form);
            return withError(model, "register", e.getMessage(), null);
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        userSessionService.logout(session);
        return "redirect:/login?loggedOut";
    }

    /**
     * Session fixation protection: always start a fresh session (new id) at login, so an id
     * planted in the browser before login can't be used to hijack the authenticated session.
     */
    private static HttpSession newSession(HttpServletRequest request) {
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        return request.getSession(true);
    }

    private static String withError(Model model, String view, String error, String login) {
        model.addAttribute("error", error);
        model.addAttribute("login", login);
        return view;
    }
}
