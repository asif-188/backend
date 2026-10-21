package com.globalisor.backend.controller;

import com.globalisor.backend.model.User;
import com.globalisor.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/staff/sticky-notes")
public class StaffStickyNoteController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getStickyNotes(@RequestParam(value = "email", required = false) String email,
                                           @RequestParam(value = "staffId", required = false) String staffId) {
        Optional<User> userOpt = Optional.empty();
        if (email != null && !email.trim().isEmpty()) {
            userOpt = userRepository.findByEmailIgnoreCase(email.trim());
        }
        if (userOpt.isEmpty() && staffId != null && !staffId.trim().isEmpty()) {
            userOpt = userRepository.findById(staffId.trim());
        }

        Map<String, Object> resp = new HashMap<>();
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            resp.put("email", user.getEmail());
            resp.put("stickyNotesJson", user.getStickyNotesJson());
            return ResponseEntity.ok(resp);
        }

        resp.put("stickyNotesJson", null);
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<?> saveStickyNotes(@RequestBody Map<String, Object> payload) {
        String email = payload.get("email") != null ? String.valueOf(payload.get("email")).trim() : null;
        String staffId = payload.get("staffId") != null ? String.valueOf(payload.get("staffId")).trim() : null;
        String notesJson = payload.get("stickyNotesJson") != null ? String.valueOf(payload.get("stickyNotesJson")) : null;

        Optional<User> userOpt = Optional.empty();
        if (email != null && !email.isEmpty()) {
            userOpt = userRepository.findByEmailIgnoreCase(email);
        }
        if (userOpt.isEmpty() && staffId != null && !staffId.isEmpty()) {
            userOpt = userRepository.findById(staffId);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setStickyNotesJson(notesJson);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Sticky notes saved successfully"));
        }

        return ResponseEntity.ok(Map.of("success", false, "message", "User not found, saved locally"));
    }
}
