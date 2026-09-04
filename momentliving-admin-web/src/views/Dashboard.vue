<template>
  <div class="page">
    <!-- 统计卡 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="stat brand-card">
          <div class="stat__num accent-num">{{ card.value }}</div>
          <div class="stat__label">{{ card.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表：折线（申请趋势） + 环形（分类分布） -->
    <el-row :gutter="16" class="charts">
      <el-col :span="12">
        <div class="chart brand-card">
          <div class="chart__title">近 7 日入驻申请趋势</div>
          <div ref="trendRef" class="chart__box"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="chart brand-card">
          <div class="chart__title">店铺分类分布</div>
          <div ref="pieRef" class="chart__box"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { getDashboard } from '../api/admin'

const data = ref(null)
const trendRef = ref(null)
const pieRef = ref(null)
let trendChart = null
let pieChart = null

const cards = computed(() => [
  { label: '店铺总数', value: data.value ? data.value.shopCount : '-' },
  { label: '优惠券总数', value: data.value ? data.value.voucherCount : '-' },
  { label: '商家账号', value: data.value ? data.value.merchantCount : '-' },
  { label: '待审核申请', value: data.value ? data.value.pendingApplyCount : '-' }
])

function renderTrend(list) {
  trendChart = echarts.init(trendRef.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: list.map((d) => d.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line',
      smooth: true,
      data: list.map((d) => d.count),
      itemStyle: { color: '#6B8E5A' },
      areaStyle: { color: 'rgba(107, 142, 90, 0.12)' }
    }]
  })
}

function renderPie(list) {
  pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 家 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      data: list.map((d) => ({ name: d.name, value: d.count })),
      color: ['#6B8E5A', '#D4A574', '#8FAC80', '#4A6B4D', '#C9B27E', '#A5B8CE', '#C48F7E', '#9BB39B']
    }]
  })
}

onMounted(async () => {
  try {
    data.value = await getDashboard()
    renderTrend(data.value.applyTrend || [])
    renderPie(data.value.typeDistribution || [])
    window.addEventListener('resize', onResize)
  } catch (e) {
    // 错误已统一 toast
  }
})

function onResize() {
  trendChart && trendChart.resize()
  pieChart && pieChart.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart && trendChart.dispose()
  pieChart && pieChart.dispose()
})
</script>

<style scoped>
.stat {
  padding: 22px 26px;
}
.stat__num {
  font-size: 30px;
}
.stat__label {
  margin-top: 6px;
  color: var(--text-sub);
  font-size: 13px;
}
.charts {
  margin-top: 16px;
}
.chart {
  padding: 20px;
}
.chart__title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
}
.chart__box {
  height: 300px;
}
</style>
