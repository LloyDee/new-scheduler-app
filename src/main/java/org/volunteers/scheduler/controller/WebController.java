package org.volunteers.scheduler.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
    @GetMapping("/")
    String index(HttpSession session) {
        Long id = (Long) session.getAttribute("activeEvent");
        return id == null ? "redirect:/events" : "redirect:/events/" + id + "/home";
    }
    @GetMapping("/settings")
    String settings(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return "pages/settings";
    }
}
