package com.kaiyikang.winter.web;

import java.util.Map;

import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// ViewResolver 寻找视图 - 返回View 对象
// View.render() - 输出html/json
// 暂时没有找到它的用途
public interface View {
    @Nullable
    default String getContentType() {
        return null;
    }

    void render(@Nullable Map<String, Object> model, HttpServletRequest request, HttpServletResponse response)
            throws Exception;
}
