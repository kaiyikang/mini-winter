package com.kaiyikang.winter.context;

import java.util.List;

public interface ApplicationContext extends AutoCloseable {

    boolean containsBean(String name);

    <T> T getBean(String name);

    <T> T getBean(String name, Class<T> requiredType);

    <T> T getBean(Class<T> requiredType);

    <T> List<T> getBeans(Class<T> requiredType);

    void close();
}
