#!/usr/bin/env bash
# ============================================================
# 一键初始化：装 IK 分词器 → 重启 ES → 验证 → 触发店铺索引全量导入
# 在 docker-compose.yml 同目录执行：bash setup.sh
# ============================================================
set -e

IK_VERSION="7.17.18"
# ★ 用 infinilabs 官方发布站（GitHub release 直连经常 404/超时，2026-08-29 实测）
IK_URL="https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-${IK_VERSION}.zip"
# 触发全量导入的地址：shop-service 直接端口（脚本在 Docker 宿主机上跑时用 localhost）
REINDEX_URL="${REINDEX_URL:-http://localhost:8082/shop/es/reindex}"

echo "==> 1/5 安装 IK 分词器 ${IK_VERSION}"
if docker exec momentliving-es elasticsearch-plugin list | grep -q "analysis-ik"; then
  echo "    IK 已安装，跳过"
else
  # 先下载到宿主机，再 docker cp 进容器安装（容器内直连外网不稳定）
  curl -sL -o /tmp/analysis-ik-${IK_VERSION}.zip "${IK_URL}"
  docker cp /tmp/analysis-ik-${IK_VERSION}.zip momentliving-es:/tmp/
  docker exec momentliving-es elasticsearch-plugin install --batch "file:///tmp/analysis-ik-${IK_VERSION}.zip"
fi

echo "==> 2/5 重启 ES 使插件生效"
docker restart momentliving-es
echo "    等待 ES 就绪"
for i in $(seq 1 60); do
  if curl -s -m 2 http://localhost:9200 >/dev/null 2>&1; then break; fi
  sleep 2
done
curl -s http://localhost:9200/_cluster/health | head -c 200; echo

echo "==> 3/5 验证 IK 分词"
echo "-- ik_smart（粗切分，搜索用）:"
curl -s -H 'Content-Type: application/json' \
  http://localhost:9200/_analyze -d '{"analyzer":"ik_smart","text":"老城老火锅"}'; echo
echo "-- ik_max_word（细切分，索引用）:"
curl -s -H 'Content-Type: application/json' \
  http://localhost:9200/_analyze -d '{"analyzer":"ik_max_word","text":"老城老火锅"}'; echo

echo "==> 4/5 等待 Kibana（http://localhost:5601，首次启动约 1-2 分钟）"
docker ps --filter name=momentliving-kibana --format '{{.Names}} {{.Status}}'

echo "==> 5/5 触发 shop-service 全量导入（需 shop-service 已启动；失败可稍后手动执行）"
echo "    curl -X POST ${REINDEX_URL}"
curl -s -X POST "${REINDEX_URL}" 2>/dev/null || echo "    （shop-service 未启动，跳过——启动后手动执行上面这条）"

echo ""
echo "完成！下一步："
echo "  1. 确认 momentliving-shop-service 的 momentliving.elasticsearch.uris 指向本机（默认 http://192.168.19.131:9200，本机 Docker 改 localhost:9200）"
echo "  2. 重启 shop-service（或手动 curl -X POST ${REINDEX_URL}）"
echo "  3. 前端搜索框试搜：\"火锅\"（分词命中）/ \"城锅\"（ik 词组跨字命中，like 做不到）"
