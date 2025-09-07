package com.kaiyikang.winter.io;

import java.io.IOException;
import java.io.InputStream;

// For an interface to be considered a functional interface, it must contain exactly one abstract method.
@FunctionalInterface
public interface InputStreamCallback<T> {

    T doWithInputStream(InputStream stream) throws IOException;
}
