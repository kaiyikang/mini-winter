package com.kaiyikang.hello.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.hello.User;
import com.kaiyikang.hello.service.UserService;
import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.Controller;
import com.kaiyikang.winter.annotation.GetMapping;
import com.kaiyikang.winter.annotation.PostMapping;
import com.kaiyikang.winter.annotation.RequestParam;
import com.kaiyikang.winter.exception.DataAccessException;
import com.kaiyikang.winter.web.ModelAndView;

import jakarta.servlet.http.HttpSession;

@Controller
public class MvcController {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    UserService userService;

    static final String USER_SESSION_KEY = "__user__";

    @GetMapping("/")
    ModelAndView index(HttpSession session) {
        User user = (User) session.getAttribute(USER_SESSION_KEY);
        if (user == null) {
            return new ModelAndView("redirect:/register");
        }
        return new ModelAndView("/index.html", Map.of("user", user));
    }

    @GetMapping("/register")
    ModelAndView register() {
        return new ModelAndView("/register.html");
    }

    @PostMapping("/register")
    ModelAndView doRegister(@RequestParam("email") String email, @RequestParam("name") String name,
            @RequestParam("password") String password) {
        try {
            userService.createUser(email, name, password);
        } catch (DataAccessException e) {
            return new ModelAndView("/register.html", Map.of("error", "Email already exist."));
        }
        return new ModelAndView("redirect:/signin");
    }

    @GetMapping("/signin")
    ModelAndView signin() {
        return new ModelAndView("/signin.html");
    }

    @PostMapping("/signin")
    ModelAndView doSignin(@RequestParam("email") String email, @RequestParam("password") String password,
            HttpSession session) {
        User user = null;
        try {
            user = userService.getUser(email.strip().toLowerCase());
            if (!user.password.equals(password)) {
                throw new DataAccessException("bad password.");
            }
        } catch (DataAccessException e) {
            // user not found:
            return new ModelAndView("/signin.html", Map.of("error", "Bad email or password."));
        }
        session.setAttribute(USER_SESSION_KEY, user);
        return new ModelAndView("redirect:/");
    }

    @GetMapping("/signout")
    String signout(HttpSession session) {
        session.removeAttribute(USER_SESSION_KEY);
        return "redirect:/signin";
    }

}