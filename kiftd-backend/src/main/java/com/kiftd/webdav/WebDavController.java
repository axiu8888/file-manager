package com.kiftd.webdav;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class WebDavController {

    private final WebDavServlet webDavServlet;

    public WebDavController(WebDavServlet webDavServlet) {
        this.webDavServlet = webDavServlet;
    }

    @RequestMapping({"/webdav", "/webdav/**"})
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        webDavServlet.service(request, response);
    }
}
