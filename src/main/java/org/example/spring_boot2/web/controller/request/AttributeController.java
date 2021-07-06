package org.example.spring_boot2.web.controller.request;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lifei
 */
@Controller
public class AttributeController {
    @GetMapping("/goto_page")
    public String gotoPage(HttpServletRequest request) {
        request.setAttribute("code", 200);
        request.setAttribute("msg", "成功...");
        return "forward:/attributes";
    }

    @ResponseBody
    @GetMapping("/attributes")
    public Map<String, Object> getAttributes(@RequestAttribute("code") Integer code,
                                             HttpServletRequest request) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("code", code);
        map.put("msg", request.getAttribute("msg"));
        return map;
    }
}
