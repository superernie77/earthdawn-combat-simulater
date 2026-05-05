package com.earthdawn.controller;

import com.earthdawn.model.UserAccount;
import com.earthdawn.repository.UserAccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountRepository accountRepo;

    @GetMapping
    public List<UserAccount> findAll() {
        return accountRepo.findAllByOrderByUsernameAsc();
    }

    @PostMapping
    public UserAccount create(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Benutzername darf nicht leer sein");
        }
        if (accountRepo.existsByUsername(username.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Benutzername bereits vergeben: " + username);
        }
        return accountRepo.save(UserAccount.builder().username(username.trim()).build());
    }

    @PatchMapping("/{id}/gamemaster")
    public UserAccount setGamemaster(@PathVariable Long id, @RequestParam boolean value) {
        UserAccount account = accountRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Konto nicht gefunden: " + id));
        account.setGamemaster(value);
        return accountRepo.save(account);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!accountRepo.existsById(id)) {
            throw new EntityNotFoundException("Konto nicht gefunden: " + id);
        }
        accountRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
