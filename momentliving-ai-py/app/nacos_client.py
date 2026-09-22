"""Nacos 注册客户端（让 Spring Cloud Gateway 用 lb://ai-py-service 找到 Python 服务）。

只做注册 + 心跳，配置不走 Nacos。注册失败仅告警不阻断启动。
"""
import socket
import threading

from app.config import Settings
from app.logging_config import get_logger

logger = get_logger(__name__)

SERVICE_NAME = "ai-py-service"


def _get_local_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def register_to_nacos(settings: Settings, port: int):
    """注册 Python 服务到 Nacos（v1 HTTP SDK，同步 API）。返回客户端实例。"""
    import nacos

    ip = _get_local_ip()
    client = nacos.NacosClient(
        server_addresses=settings.nacos.server_addr,
        namespace=settings.nacos.namespace,
        username=settings.nacos.username,
        password=settings.nacos.password,
    )
    client.add_naming_instance(
        service_name=SERVICE_NAME,
        ip=ip,
        port=port,
        cluster_name="DEFAULT",
        weight=1.0,
        metadata={"version": "0.1.0", "protocol": "http", "framework": "fastapi"},
        healthy=True,
        ephemeral=True,
    )

    def _heartbeat():
        import time

        while True:
            try:
                client.send_heartbeat(SERVICE_NAME, ip, port, weight=1.0)
            except Exception:
                pass
            time.sleep(5)

    threading.Thread(target=_heartbeat, daemon=True).start()
    logger.info("已注册到 Nacos", service_name=SERVICE_NAME, ip=ip, port=port)
    return client, ip


def deregister_from_nacos(client, settings: Settings, port: int) -> None:
    try:
        client.remove_naming_instance(SERVICE_NAME, _get_local_ip(), port)
    except Exception as e:
        logger.warning("Nacos 反注册失败", error=str(e))
