package br.com.example.sample.controller;

import br.com.example.sample.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private RedisService redisService;

    @PostMapping("/create")
    public String createUser(@RequestParam String userId, @RequestParam String userName) {
        redisService.createUser(userId, userName);
        return "User created successfully!";
    }

    @GetMapping("/get")
    public String getUser(@RequestParam String userId) {
        return redisService.getUser(userId);
    }

    @PutMapping("/update")
    public String updateUser(@RequestParam String userId, @RequestParam String newUserName) {
        redisService.updateUser(userId, newUserName);
        return "User updated successfully!";
    }

    @DeleteMapping("/delete")
    public String deleteUser(@RequestParam String userId) {
        redisService.deleteUser(userId);
        return "User deleted successfully!";
    }
}