package org.example.spring_boot2.web.controller.request;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lifei
 */
@Controller
public class ServletController {
    @GetMapping("/servlet1")
    public String getServlet1(Map<String, Object> map,
                              Model model,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        map.put("hello", "world666");
        model.addAttribute("world", "hello666");
        request.setAttribute("message", "hello world");
        Cookie cookie = new Cookie("c1", "v1");
        response.addCookie(cookie);
        return "forward:/servlet1/result";
    }

    @ResponseBody
    @GetMapping("/servlet1/result")
    public Map<String, Object> getServlet1Result(@RequestAttribute("hello") String hello,
                                                 @RequestAttribute("world") String world,
                                                 @RequestAttribute("message") String message) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("hello", hello);
        map.put("world", world);
        map.put("message", message);
        return map;
    }
}
