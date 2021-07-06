package org.example.spring_boot2.web.controller.request;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lifei
 */
@RestController
public class ParameterController {
    @GetMapping("/path/{path1}/{path2}")
    public Map<String, Object> getCar(@PathVariable("path1") Integer path1,
                                      @PathVariable("path2") String path2,
                                      @PathVariable Map<String, String> paths) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("path1", path1);
        map.put("path2", path2);
        map.put("paths", paths);
        return map;
    }

    @GetMapping("/headers")
    public Map<String, Object> getHeaders(@RequestHeader("User-Agent") String userAgent,
                                          @RequestHeader Map<String, String> headers) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("userAgent", userAgent);
        map.put("headers", headers);
        return map;
    }

    @GetMapping("/params")
    public Map<String, Object> getParams(@RequestParam("age") Integer age,
                                         @RequestParam("inters") List<String> inters,
                                         @RequestParam Map<String, Object> params) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("age", age);
        map.put("inters", inters);
        map.put("params", params);
        return map;
    }

    @GetMapping("/cookies")
    public Map<String, Object> getCookies(@CookieValue("JSESSIONID") String sessionId,
                                          @CookieValue("JSESSIONID") Cookie cookie) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("sessionId", sessionId);
        map.put("cookie", cookie);
        return map;
    }

    @PostMapping("/body")
    public Map<String, Object> getBody(@RequestBody String formBody) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("formBody", formBody);
        return map;
    }
}
