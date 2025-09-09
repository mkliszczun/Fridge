package com.example.demo.repository;

import com.example.demo.fridge.Fridge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FridgeRepository extends JpaRepository<Fridge, UUID> {
}