package org.example.spring_boot2.web.controller.request;

import org.springframework.web.bind.annotation.*;

/**
 * @author lifei
 */
@RestController
public class RequestController {
    @GetMapping("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.GET)
    public String getUser() {
        return "GET-张三";
    }

    @PostMapping("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.POST)
    public String saveUser() {
        return "POST-张三";
    }

    @PutMapping("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.PUT)
    public String putUser() {
        return "PUT-张三";
    }

    @DeleteMapping("/user")
    // @RequestMapping(value = "/user", method = RequestMethod.DELETE)
    public String deleteUser() {
        return "DELETE-张三";
    }
}
