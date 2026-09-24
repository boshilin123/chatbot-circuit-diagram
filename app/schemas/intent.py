from pydantic import BaseModel, Field


class SearchIntent(BaseModel):
    """从用户问题中抽取的车辆电路图检索意图。"""

    brand: str | None = Field(
        default=None,
        description="车辆或工程机械品牌，例如东风、三一、徐工。",
    )
    model: str | None = Field(
        default=None,
        description="车型或系列，例如天龙、SY60、XE135G。",
    )
    component: str | None = Field(
        default=None,
        description="用户要查找的部件，例如仪表、发动机、液压电脑板。",
    )
    ecu_type: str | None = Field(
        default=None,
        description="明确出现的 ECU、控制器或电脑板型号，例如 EDC17C53。",
    )
    document_type: str | None = Field(
        default=None,
        description="资料类型，例如针脚定义、整车电路图、线路图。",
    )
    keywords: list[str] = Field(
        default_factory=list,
        description="除上述字段外仍有检索价值的关键词，只保留用户原问题中明确存在的信息。",
    )
