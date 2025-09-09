package com.example.demo.controller;

import com.example.demo.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {


    @GetMapping("/hello")
    @ResponseBody
    public String helloController(){
        return("Hello User or Admin");
    }

    @GetMapping("/admin")
    @ResponseBody
    public String adminController(){
        return("Hello Admin only");
    }

    @GetMapping("/public")
    @ResponseBody
    public String publicMapping() {
        return("Hello all");
    }


}
