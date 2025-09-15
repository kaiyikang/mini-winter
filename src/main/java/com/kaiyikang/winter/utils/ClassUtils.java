package com.kaiyikang.winter.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.kaiyikang.winter.annotation.Bean;
import com.kaiyikang.winter.annotation.Component;
import com.kaiyikang.winter.exception.BeanDefinitionException;

import jakarta.annotation.Nullable;

public class ClassUtils {

    public static <A extends Annotation> A findAnnotation(Class<?> clazz, Class<A> targetAnnotation) {
        A a = clazz.getAnnotation(targetAnnotation);
        // If the annotation cannot be found
        for (Annotation anno : clazz.getAnnotations()) {
            // Get the definition of the annotation
            Class<? extends Annotation> annoType = anno.annotationType();

            if (annoType.getPackageName().equals("java.lang.annotation")) {
                continue;
            }

            A found = findAnnotation(annoType, targetAnnotation);
            if (found == null) {
                continue;
            }

            if (a != null) {
                throw new BeanDefinitionException("Duplicate @" + targetAnnotation.getSimpleName()
                        + " found on class " + clazz.getSimpleName());
            }

            a = found;
        }
        return a;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A getAnnotation(Annotation[] annotations, Class<A> targetAnnotation) {
        for (Annotation anno : annotations) {
            if (targetAnnotation.isInstance(anno)) {
                return (A) anno;
            }
        }
        return null;
    }

    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        if (bean == null) {
            return method.getName();
        }

        String name = bean.value();
        if (name.isEmpty()) {
            // Use method name if no value in the annotation
            name = method.getName();
        }
        return name;

        // return
        // Optional.ofNullable(method.getAnnotation(Bean.class))
        // .map(Bean::value)
        // .filter(name -> !name.isEmpty())
        // .orElse(method.getName());
    }

    public static String getBeanName(Class<?> clazz) {
        Component component = findAnnotation(clazz, Component.class);

        if (component != null && !component.value().isEmpty()) {
            return component.value();
        }

        String name = clazz.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    @Nullable
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> targetAnnotation) {
        Method foundedMethod = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(targetAnnotation)) {
                // Verify the number of parameters
                if (method.getParameterCount() != 0) {
                    throw new BeanDefinitionException(
                            String.format(
                                    "Method '%s' with @%s must not have argument: %s",
                                    method.getName(), targetAnnotation.getSimpleName(), clazz.getName()));
                }
                // Verify the number of parameters
                if (foundedMethod != null) {
                    throw new BeanDefinitionException(String.format(
                            "Multiple methods with @%s found in class: %s",
                            targetAnnotation.getSimpleName(), clazz.getName()));
                }
                foundedMethod = method;
            }
        }
        return foundedMethod;
    }

    public static Method getNamedMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (ReflectiveOperationException e) {
            throw new BeanDefinitionException(String.format(
                    "Method '%s' not found in class: %s",
                    methodName, clazz.getName()));
        }
    }
}
