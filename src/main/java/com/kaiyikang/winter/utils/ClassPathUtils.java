package com.kaiyikang.winter.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import com.kaiyikang.winter.io.InputStreamCallback;

public class ClassPathUtils {

    public static <T> T readInputStream(String path, InputStreamCallback<T> callback) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        try (InputStream input = getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return InputStreamCallback.doWithInputStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }
    }

    static ClassLoader getContextClassLoader() {
        ClassLoader cl = null;
        cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassPathUtils.class.getClassLoader();
        }
        return cl;
    }
}
