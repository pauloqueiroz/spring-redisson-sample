package br.com.example.sample.config;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer();
        serverConfig.setAddress("redis://localhost:6379");
        //serverConfig.setPassword(""); // Add if you have password set for Redis

        return org.redisson.Redisson.create(config);
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() throws Exception {
        Config config = Config.fromYAML(RedisConfig.class.getClassLoader().getResource("redisson.yaml"));
        return Redisson.create(config);
    }
}