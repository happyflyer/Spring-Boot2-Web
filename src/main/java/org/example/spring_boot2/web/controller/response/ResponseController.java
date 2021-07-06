package org.example.spring_boot2.web.controller.response;

import org.example.spring_boot2.web.bean.Person;
import org.example.spring_boot2.web.bean.Pet;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

/**
 * @author lifei
 */
@Controller
@RequestMapping("/response")
public class ResponseController {
    @ResponseBody
    @GetMapping("/person")
    public Person getPerson() {
        Person person = new Person();
        person.setUserName("张三");
        person.setAge(18);
        person.setBirth(new Date());
        Pet pet = new Pet();
        pet.setName("小猫");
        pet.setAge(2);
        person.setPet(pet);
        return person;
    }
}
