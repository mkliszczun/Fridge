package com.example.demo.controller;

import com.example.demo.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AdminService adminService;

    public ApiController(AdminService adminService){
        this.adminService = adminService;
    }

    @GetMapping("admin")
    String provideData(){
        return adminService.getAdminSecret();
    }
    @GetMapping("normal")
    String commodData(){
        return adminService.getCommonData();
    }

}
