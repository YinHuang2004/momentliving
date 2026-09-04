package com.momentliving.task;

import com.momentliving.mapper.ShopMapper;
import com.momentliving.service.ShopBloomFilterService;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

//布隆过滤器底层是byte数组，存入id值时根据hash算法算出hash值再将hash值转成二进制，判断id是否存在时就查询位置到底是0还是1
@Component
//实现了ApplicationRunner接口 → SpringBoot 容器完全启动完毕后自动执行 run () 方法，不用手动跑测试类，服务一上线就加载数据。
//对一个 Long 类型 ID，经过多个不同哈希函数算出多个数组下标；
//存入：把对应下标位置的二进制 bit 置为1；
//判断存在：校验所有下标 bit 是否全为 1；只要有一个是 0 → 一定不存在；全部为 1 → 可能存在（存在极小概率误判）；
//缺点：不支持单个元素删除，所以删除店铺只能靠每日定时任务重建整个过滤器（双布隆重建无感）。
public class BloomFilterInitTask implements ApplicationRunner {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ShopBloomFilterService shopBloomFilterService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 启动初始化：把 DB 全量店铺 ID 灌入 active 桶（flag 不存在时默认 0，不碰 backup）
        List<Long> shopIds = shopMapper.selectAllIds();
        shopBloomFilterService.initActive(shopIds);
    }
}
