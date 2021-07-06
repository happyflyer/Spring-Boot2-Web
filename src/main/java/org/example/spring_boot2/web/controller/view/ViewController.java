package org.example.spring_boot2.web.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author lifei
 */
@Controller
@RequestMapping("/view")
public class ViewController {
    @GetMapping("/success")
    public String success(Model model) {
        model.addAttribute("msg", "你好");
        model.addAttribute("link", "https://cn.bing.com/");
        return "success";
    }
}
