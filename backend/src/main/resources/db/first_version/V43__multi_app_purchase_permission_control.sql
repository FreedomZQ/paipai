-- 多应用购买权限控制配置。
-- 说明：
-- 1. namespace_code='billing_purchase_control' 为统一计费层保留命名空间，可被不同 app_code 复用。
-- 2. config_key='global' 控制某个应用整体是否可购买。
-- 3. config_key='products.{商品编码}' 控制某个应用内单个购买项是否置灰禁用。
-- 4. messages 按语言编码保存前端展示文案，后续新增语言只需追加对应 key。

INSERT INTO public.sys_remote_config (
    app_code,
    namespace_code,
    config_key,
    config_value_json,
    status,
    created_at,
    updated_at
)
VALUES
    (
        'paipai_readingcompanion',
        'billing_purchase_control',
        'global',
        '{
            "value": {
                "allowed": true,
                "status": "available",
                "reasonCode": "purchase_available",
                "messageKey": "purchase_available",
                "messages": {
                    "zh-Hans": "",
                    "en": "",
                    "ja": "",
                    "ko": "",
                    "es": ""
                }
            }
        }'::jsonb,
        'active',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'paipai_readingcompanion',
        'billing_purchase_control',
        'products.ocr_180',
        '{
            "value": {
                "allowed": false,
                "status": "disabled",
                "reasonCode": "product_temporarily_disabled",
                "messageKey": "service_unavailable",
                "messages": {
                    "zh-Hans": "服务暂不可用",
                    "en": "Service is temporarily unavailable",
                    "ja": "サービスは一時的に利用できません",
                    "ko": "서비스를 일시적으로 사용할 수 없습니다",
                    "es": "El servicio no está disponible temporalmente"
                }
            }
        }'::jsonb,
        'active',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
ON CONFLICT DO NOTHING;
