package com.apphub.backend.sys.billing.privacy.mapper;

import com.apphub.backend.sys.billing.privacy.entity.SysPrivacyConsentEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysPrivacyConsentMapper extends BaseMapper<SysPrivacyConsentEntity> {
    @Select("""
        SELECT *
        FROM sys_privacy_consent
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND consent_type = #{consentType}
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    SysPrivacyConsentEntity selectLatest(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("consentType") String consentType
    );
}
