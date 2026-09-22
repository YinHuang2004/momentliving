"""Tools 总入口（统一暴露给 ChatService）。

C 端对话工具集 = BLOG + SHOP + VOUCHER 共 6 个（不挂 MerchantTools，
商家的经营分析走独立的 /ai/merchant/** 端点，身份由 X-Merchant-Id 决定）。
"""
from app.tools.blog_tools import get_hot_blogs
from app.tools.shop_tools import get_shop_detail, get_shop_reviews, search_shops
from app.tools.voucher_tools import get_shop_vouchers, get_user_vouchers

C_END_TOOLS = [
    get_hot_blogs,
    search_shops,
    get_shop_detail,
    get_shop_reviews,
    get_user_vouchers,
    get_shop_vouchers,
]
