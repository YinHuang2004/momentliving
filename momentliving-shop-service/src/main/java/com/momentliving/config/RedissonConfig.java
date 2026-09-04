package com.momentliving.config;
import lombok.Data;
        import org.redisson.Redisson;
        import org.redisson.api.RedissonClient;
        import org.redisson.config.Config;
        import org.springframework.beans.factory.annotation.Value;
        import org.springframework.boot.context.properties.ConfigurationProperties;
        import org.springframework.context.annotation.Bean;
        import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedissonConfig {

    private String host;
    private int port;
    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setPassword(password)
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(10);
        return Redisson.create(config);
    }
}