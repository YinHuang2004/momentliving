package com.momentliving.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.api.client.UserClient;
import com.momentliving.api.client.VoucherClient;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.DashboardMapper;
import com.momentliving.mapper.MerchantApplyMapper;
import com.momentliving.entity.MerchantApply;
import com.momentliving.result.Result;
import com.momentliving.vo.DashboardVO;
import com.momentliving.vo.RecentVerifyVO;
import com.momentliving.vo.UserVO;
import com.momentliving.vo.VerifyRecordsVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理后台专属接口（平台管理员登录态，AdminAuthInterceptor 保护）
 * - GET /admin/dashboard        数据概览（统计卡 + 图表数据）
 * - GET /admin/verify/records   按店铺查看核销记录（核销记录页，shopId 必填）
 */
@RestController
@Slf4j
public class AdminDashboardController {

    @Resource
    private DashboardMapper dashboardMapper;
    @Resource
    private MerchantApplyMapper merchantApplyMapper;
    @Resource
    private VoucherClient voucherClient;
    @Resource
    private UserClient userClient;

    /** 数据概览：4 个统计卡 + 分类分布（环形图）+ 近 7 日申请趋势（折线图） */
    @GetMapping("/admin/dashboard")
    public Result<DashboardVO> dashboard() {
        // 近 7 日趋势补零：SQL 只返回有申请的日期，缺的日期在 Java 侧补 0
        List<DashboardVO.DateCount> trend = buildTrend(dashboardMapper.applyTrend());

        List<DashboardVO.TypeCount> types = dashboardMapper.typeDistribution().stream()
                .map(row -> DashboardVO.TypeCount.builder()
                        .name(String.valueOf(row.get("name")))
                        .count(((Number) row.get("cnt")).longValue())
                        .build())
                .toList();

        return Result.success(DashboardVO.builder()
                .shopCount(dashboardMapper.countShop())
                .voucherCount(dashboardMapper.countVoucher())
                .pendingApplyCount(merchantApplyMapper.selectCount(new LambdaQueryWrapper<MerchantApply>()
                        .eq(MerchantApply::getStatus, MerchantApply.STATUS_PENDING)))
                .merchantCount(dashboardMapper.countMerchant())
                .typeDistribution(types)
                .applyTrend(trend)
                .build());
    }

    /** 补齐近 7 天缺失日期为 0，保证折线图 X 轴连续 */
    private List<DashboardVO.DateCount> buildTrend(List<Map<String, Object>> rows) {
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byDate.put(String.valueOf(row.get("d")), ((Number) row.get("cnt")).longValue());
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<DashboardVO.DateCount> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).format(fmt);
            trend.add(DashboardVO.DateCount.builder().date(day).count(byDate.getOrDefault(day, 0L)).build());
        }
        return trend;
    }

    /**
     * 按店铺查看核销记录（shopId 必填：管理端不默认归属任何店铺），
     * 买家昵称编排 user-service 回填。
     */
    @GetMapping("/admin/verify/records")
    public Result<VerifyRecordsVO> verifyRecords(@RequestParam("shopId") Long shopId,
                                                 @RequestParam(value = "status", required = false) Integer status,
                                                 @RequestParam(value = "current", defaultValue = "1") Integer current,
                                                 @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        if (shopId == null) {
            throw new BadRequestException("缺少店铺参数");
        }
        VerifyRecordsVO records = voucherClient
                .recordsByShop(shopId, status, current, pageSize).getData();
        if (records != null) {
            fillNickNames(records.getList());
        }
        return Result.success(records);
    }

    /** 批量回填核销记录的买家昵称（user-service 按用户 id 批查） */
    private void fillNickNames(List<RecentVerifyVO> recent) {
        if (recent == null || recent.isEmpty()) {
            return;
        }
        List<Long> userIds = recent.stream()
                .map(RecentVerifyVO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }
        Result<List<UserVO>> usersRes = userClient.selectUsersByIdsOrdered(userIds);
        if (usersRes != null && usersRes.getData() != null) {
            Map<Long, String> nickMap = usersRes.getData().stream()
                    .filter(u -> u != null && u.getId() != null)
                    .collect(Collectors.toMap(UserVO::getId, UserVO::getNickName, (a, b) -> a));
            recent.forEach(r -> r.setNickName(nickMap.get(r.getUserId())));
        }
    }
}
