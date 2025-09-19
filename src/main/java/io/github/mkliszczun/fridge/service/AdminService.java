package io.github.mkliszczun.fridge.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    @PreAuthorize("hasRole('ADMIN')")
    public String getAdminSecret(){
        return("Admin's Secret");
    }

    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public String getCommonData(){
        return("Some commod Data");
    }
}
