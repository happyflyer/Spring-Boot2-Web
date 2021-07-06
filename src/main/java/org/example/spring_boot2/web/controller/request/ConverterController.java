package org.example.spring_boot2.web.controller.request;

import org.example.spring_boot2.web.bean.Person;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @author lifei
 */
@RestController
public class ConverterController {
    @PostMapping("/convert")
    public Person getObject(Person person) {
        return person;
    }
}
