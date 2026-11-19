package com.momentliving;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest
@Disabled("需要可用的 Redis/Redisson，不属于默认单元测试")
@Slf4j
public class test {
    @Resource
    private RedissonClient redissonClient;
    private RLock lock;
    @Test
    public void test1(){
        boolean islock=false;
        lock=redissonClient.getLock("lock");
        try{
            islock = lock.tryLock();
            if(!islock){
                log.error("获取锁失败，1");
                return;
            }
            log.info("获取锁成功，1");
            test2();

        }finally{
            if(islock){
                log.info("释放锁，1");
                lock.unlock();
            }
        }
    }
    @Test
    public void test2() {
        boolean islock = false;
        try {
            islock = lock.tryLock();
            if (!islock) {
                log.error("获取锁失败，2");
                return;
            }
        } finally {
            if (islock) {
                log.info("释放锁，2");
                lock.unlock();
            }
        }
    }
}
