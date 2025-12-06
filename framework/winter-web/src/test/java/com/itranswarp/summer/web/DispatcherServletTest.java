package com.itranswarp.summer.web;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockServletContext;

import com.kaiyikang.winter.web.DispatcherServlet;

import jakarta.servlet.ServletException;

public class DispatcherServletTest {

    DispatcherServlet dispatcherServlet;
    MockServletContext ctx;

    @BeforeEach
    void init() throws ServletException {
        this.ctx = createMockServletContext();
        https://liaoxuefeng.com/books/summerframework/web/mvc/index.html
        https://github.com/michaelliao/summer-framework/blob/main/framework/summer-web/src/test/java/com/itranswarp/summer/web/DispatcherServletTest.java
    }
}
