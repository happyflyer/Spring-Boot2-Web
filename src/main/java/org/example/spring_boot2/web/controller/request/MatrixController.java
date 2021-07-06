package org.example.spring_boot2.web.controller.request;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lifei
 */
@RestController
public class MatrixController {
    @GetMapping("/matrix1/{path}")
    public Map<String, Object> getMatrix1(@MatrixVariable("low") Integer low,
                                          @MatrixVariable("brand") List<String> brand,
                                          @PathVariable("path") String path) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("low", low);
        map.put("brand", brand);
        map.put("path", path);
        return map;
    }

    @GetMapping("/matrix2/{path1}/{path2}")
    public Map<String, Object> getMatrix2(@MatrixVariable(name = "age", pathVar = "path1") Integer age1,
                                          @PathVariable("path1") String path1,
                                          @MatrixVariable(name = "age", pathVar = "path2") Integer age2,
                                          @PathVariable("path2") String path2) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("age1", age1);
        map.put("path1", path1);
        map.put("age2", age2);
        map.put("path3", path2);
        return map;
    }
}
