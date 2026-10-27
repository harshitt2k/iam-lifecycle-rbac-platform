package com.iam.api;

import com.iam.cli.AppContext;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thin web adapter: all the logic is in ApiRouter. Served at /iam-api/api/... */
@WebServlet(name = "IamApi", urlPatterns = "/api/*")
public class ApiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final int MAX_BODY_CHARS = 64 * 1024;

    private transient ApiRouter router;

    @Override
    public void init() {
        router = new ApiRouter(new AppContext());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String method = req.getMethod();

        String body = "";
        if (!"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            try (BufferedReader r = req.getReader()) {
                int n;
                while ((n = r.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                    if (sb.length() > MAX_BODY_CHARS) {
                        write(resp, new ApiResponse(413, "application/json; charset=UTF-8",
                                "{\"error\":\"Request body too large\"}"));
                        return;
                    }
                }
            }
            body = sb.toString();
        }

        Map<String, String> query = new LinkedHashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (v.length > 0) query.put(k, v[0]);
        });

        write(resp, router.handle(method, req.getPathInfo(), query, req.getHeader("X-Actor"), body));
    }

    private static void write(HttpServletResponse resp, ApiResponse r) throws IOException {
        resp.setStatus(r.status());
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType(r.contentType());
        resp.setHeader("Cache-Control", "no-store");
        if (r.allow() != null) resp.setHeader("Allow", r.allow());
        resp.getWriter().write(r.body());
    }
}
