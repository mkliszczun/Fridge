package com.example.demo.service;


import com.example.demo.fridge.Fridge;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public interface FridgeService {
    Fridge createFridge(String name, UUID currentUserId);
    void deleteFridge(UUID fridgeId, UUID currentUserId, boolean hardDeleteIfEmpty);
    Fridge requireMembership(UUID fridgeId, UUID currentUserId);
    List<Fridge> listMyFridges(UUID currentUserId);
    long countMembers(UUID fridgeId);
}
