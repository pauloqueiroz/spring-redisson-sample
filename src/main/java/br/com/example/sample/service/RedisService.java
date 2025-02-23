package br.com.example.sample.service;

import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private RedissonClient redissonClient;

    private final String MAP_NAME = "userCache";

    // Create operation
    public void createUser(String userId, String userName) {
        RMap<String, String> map = redissonClient.getMap(MAP_NAME);
        map.expire(Duration.ofMinutes(10));
        map.put(userId, userName);
    }

    // Read operation
    public String getUser(String userId) {
        RMap<String, String> map = redissonClient.getMap(MAP_NAME);
        return map.get(userId);
    }

    // Update operation
    public void updateUser(String userId, String newUserName) {
        RMap<String, String> map = redissonClient.getMap(MAP_NAME);
        if (map.containsKey(userId)) {
            map.put(userId, newUserName);
        }
    }

    // Delete operation
    public void deleteUser(String userId) {
        RMap<String, String> map = redissonClient.getMap(MAP_NAME);
        map.remove(userId);
    }
}
