package org.example.spring_boot2.web.controller.request;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author lifei
 */
@RestController
public class HelloController {
    @RequestMapping("/index")
    public String index() {
        return "index";
    }

    @RequestMapping("/img1.jpg")
    public String hello() {
        return "hello";
    }
}
