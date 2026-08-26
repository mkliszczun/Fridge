package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.enums.Unit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;

@RestController
@RequestMapping("/api/units")
public class UnitsController {
    @GetMapping
    public List<Unit> getUnits(){
        return List.copyOf(EnumSet.allOf(Unit.class));
    }
}
