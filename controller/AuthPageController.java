package org.aml.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthPageController {

    @GetMapping("/login")
    public String loginPage() {
        return "redirect:/portal/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "forward:/register.html";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forward:/forgot-password.html";
    }

    @GetMapping("/terms")
    public String termsPage() {
        return "forward:/terms.html";
    }

    @GetMapping("/privacy-policy")
    public String privacyPolicyPage() {
        return "forward:/privacy-policy.html";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage() {
        return "forward:/reset-password.html";
    }

    @GetMapping("/set-password")
    public String setPasswordPage() {
        return "forward:/set-password.html";
    }

    @GetMapping("/workspace")
    public String workspacePage() {
        return "redirect:/portal/insights";
    }
}
