package org.example.spring_boot2.web.bean;

import lombok.Data;

import java.util.Date;

/**
 * @author lifei
 */
@Data
public class Person {
    private String userName;
    private Integer age;
    private Date birth;
    private Pet pet;
}
