package com.apphub.backend.apps.reading.domain.mapper;

import com.apphub.backend.apps.reading.domain.entity.ReadingResourcePackCatalogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReadingResourcePackCatalogMapper extends BaseMapper<ReadingResourcePackCatalogEntity> {
    @Select("""
        SELECT *
        FROM reading_resource_pack_catalog
        WHERE app_code = #{appCode}
          AND status = 'active'
        ORDER BY sort_order ASC, id ASC
        """)
    List<ReadingResourcePackCatalogEntity> selectActive(@Param("appCode") String appCode);

    /**
     * 查询前端购买页需要展示的资源包。
     *
     * <p>active 表示可购买，disabled 表示数据库精细化置灰；前端会展示 disabled 项但禁止付款。
     */
    @Select("""
        SELECT *
        FROM reading_resource_pack_catalog
        WHERE app_code = #{appCode}
          AND status IN ('active', 'disabled')
        ORDER BY sort_order ASC, id ASC
        """)
    List<ReadingResourcePackCatalogEntity> selectConfigured(@Param("appCode") String appCode);

    @Select("""
        SELECT *
        FROM reading_resource_pack_catalog
        WHERE app_code = #{appCode}
          AND package_code = #{packageCode}
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    ReadingResourcePackCatalogEntity selectByPackageCode(
        @Param("appCode") String appCode,
        @Param("packageCode") String packageCode
    );

    @Update("""
        UPDATE reading_resource_pack_catalog
        SET status = #{status},
            updated_at = CURRENT_TIMESTAMP
        WHERE app_code = #{appCode}
          AND package_code = #{packageCode}
        """)
    int updateStatus(
        @Param("appCode") String appCode,
        @Param("packageCode") String packageCode,
        @Param("status") String status
    );
}
