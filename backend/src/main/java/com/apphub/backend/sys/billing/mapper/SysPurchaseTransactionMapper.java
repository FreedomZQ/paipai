package com.apphub.backend.sys.billing.mapper;

import com.apphub.backend.sys.billing.entity.SysPurchaseTransactionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MyBatis Plus Mapper 接口 `SysPurchaseTransactionMapper`。
 * 负责 计费 领域的数据访问，封装对数据库表或视图的读写操作。
 */

@Mapper
public interface SysPurchaseTransactionMapper extends BaseMapper<SysPurchaseTransactionEntity> {

    @Select("""
        SELECT user_id
        FROM sys_purchase_transaction
        WHERE app_code = #{appCode}
          AND original_transaction_id = #{originalTransactionId}
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    Long selectLatestUserIdByOriginalTransactionId(
        @Param("appCode") String appCode,
        @Param("originalTransactionId") String originalTransactionId
    );

    @Select("""
        SELECT user_id
        FROM sys_purchase_transaction
        WHERE app_code = #{appCode}
          AND app_account_token_hash = #{appAccountToken}
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    Long selectLatestUserIdByAppAccountToken(
        @Param("appCode") String appCode,
        @Param("appAccountToken") String appAccountTokenHash
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_purchase_transaction
        WHERE app_code = #{appCode}
          AND source_type = #{sourceType}
        """)
    int countByAppAndSourceType(
        @Param("appCode") String appCode,
        @Param("sourceType") String sourceType
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_purchase_transaction
        WHERE app_code = #{appCode}
          AND source_type = #{sourceType}
          AND verification_status = #{verificationStatus}
        """)
    int countByAppSourceTypeAndVerificationStatus(
        @Param("appCode") String appCode,
        @Param("sourceType") String sourceType,
        @Param("verificationStatus") String verificationStatus
    );

    @Select("""
        SELECT *
        FROM sys_purchase_transaction
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND source_type = #{sourceType}
          AND original_transaction_id = #{originalTransactionId}
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    SysPurchaseTransactionEntity selectLatestByUserSourceTypeAndOriginalTransactionId(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("sourceType") String sourceType,
        @Param("originalTransactionId") String originalTransactionId
    );

    @Select("""
        SELECT COUNT(*)
        FROM sys_purchase_transaction
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
          AND verification_status NOT IN ('verified', 'rejected', 'failed')
        """)
    int countPendingByUser(
        @Param("appCode") String appCode,
        @Param("userId") Long userId
    );

    @Select("""
        SELECT *
        FROM sys_purchase_transaction
        WHERE app_code = #{appCode}
          AND user_id = #{userId}
        ORDER BY created_at DESC, id DESC
        LIMIT #{limit}
        """)
    List<SysPurchaseTransactionEntity> selectRecentByUser(
        @Param("appCode") String appCode,
        @Param("userId") Long userId,
        @Param("limit") int limit
    );
}
