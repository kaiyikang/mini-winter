package com.kaiyikang.winter.web.utils;

import java.util.regex.Pattern;

import jakarta.servlet.ServletException;

public class PathUtils {

    public static Pattern compile(String path) throws ServletException {
        // /users/{userId} => /users/(?<userId>[^/]*)
        String regPath = path.replaceAll("\\{([a-zA-Z][a-zA-Z0-9]*)\\}", "(?<$1>[^/]*)");
        // If still have { or }
        if (regPath.indexOf('{') >= 0 || regPath.indexOf('}') >= 0) {
            throw new ServletException("Invalid path: " + path);
        }
        // 确保完整匹配
        return Pattern.compile("^" + regPath + "$");
    }
}
