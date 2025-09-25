package com.kaiyikang.winter.context;

import java.util.Objects;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ApplicationContextUtils {
    private static ApplicationContext applicationContext = null;

    @Nonnull
    public static ApplicationContext getRequiredApplicationContext() {
        return Objects.requireNonNull(getRequiredApplicationContext(), "ApplicationContext is not set.");
    }

    @Nullable
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }
}
