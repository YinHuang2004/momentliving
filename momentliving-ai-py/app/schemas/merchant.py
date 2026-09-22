"""请求模型：商家端（替代 AiMerchantCopywritingDTO）。"""
from pydantic import BaseModel, ConfigDict, Field


class CopywritingRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    voucher_desc: str = Field(..., alias="voucherDesc", min_length=1, max_length=500)
    selling_point: str | None = Field(default=None, alias="sellingPoint", max_length=200)
