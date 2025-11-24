package com.kaiyikang.winter.web.utils;

import java.rmi.ServerException;
import java.util.regex.Pattern;

public class PathUtils {

    public static Pattern compile(String path) throws ServerException {
        // /users/{userId} => /users/(?<userId>[^/]*)
        String regPath = path.replaceAll("\\{([a-zA-Z][a-zA-Z0-9]*)\\}", "(?<$1>[^/]*)");
        // If still have { or }
        if (regPath.indexOf('{') >= 0 || regPath.indexOf('}') >= 0) {
            throw new ServerException("Invalid path: " + path);
        }
        // 确保完整匹配
        return Pattern.compile("^" + regPath + "$");
    }
}
