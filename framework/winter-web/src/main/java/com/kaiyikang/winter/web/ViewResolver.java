package com.kaiyikang.winter.web;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ViewResolver {
    void init();

    // 读取名为viewName的模版，并使用model填写模版，使用req里面的信息，resp用于装载 HTML
    void render(String viewName, Map<String, Object> model, HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException;
}
