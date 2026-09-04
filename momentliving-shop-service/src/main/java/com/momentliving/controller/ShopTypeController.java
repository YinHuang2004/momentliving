package com.momentliving.controller;

import com.momentliving.entity.ShopType;
import com.momentliving.result.Result;
import com.momentliving.service.ShopTypeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {

    @Resource
    private ShopTypeService typeService;

    /**
     * 查询全部商铺类型
     */
    @GetMapping("list")
    public Result<List<ShopType>> queryTypeList() {
        List<ShopType> typeList = typeService.queryList();
        return Result.success(typeList);
    }

    /**
     * 根据 id 查询商铺类型
     */
    @GetMapping("/{id}")
    public Result<ShopType> queryTypeById(@PathVariable("id") Long id) {
        ShopType shopType = typeService.getById(id);
        return shopType == null ? Result.error("商铺类型不存在") : Result.success(shopType);
    }

    /**
     * 新增商铺类型
     */
    @PostMapping
    public Result<Long> addType(@RequestBody ShopType shopType) {
        Long id = typeService.save(shopType);
        return Result.success(id);
    }

    /**
     * 更新商铺类型
     */
    @PutMapping
    public Result<Void> updateType(@RequestBody ShopType shopType) {
        typeService.update(shopType);
        return Result.success();
    }

    /**
     * 删除商铺类型
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteType(@PathVariable("id") Long id) {
        typeService.delete(id);
        return Result.success();
    }
}
