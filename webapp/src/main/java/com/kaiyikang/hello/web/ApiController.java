package com.kaiyikang.hello.web;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaiyikang.hello.User;
import com.kaiyikang.hello.service.UserService;
import com.kaiyikang.winter.annotation.Autowired;
import com.kaiyikang.winter.annotation.GetMapping;
import com.kaiyikang.winter.annotation.PathVariable;
import com.kaiyikang.winter.annotation.RestController;
import com.kaiyikang.winter.exception.DataAccessException;

@RestController
public class ApiController {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    UserService userService;

    @GetMapping("/api/user/{email}")
    Map<String, Boolean> userExist(
            @PathVariable("email") String email) {
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        try {
            userService.getUser(email);
            return Map.of("result", Boolean.TRUE);
        } catch (DataAccessException e) {
            return Map.of("result", Boolean.FALSE);
        }
    }

    @GetMapping("/api/users")
    List<User> users() {
        return userService.getUsers();
    }

}
