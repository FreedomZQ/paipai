package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.entity.SysUserIdentityEntity;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.OffsetDateTime;

/**
 * 统一认证域数据访问边界。
 *
 * <p>中文说明：上层认证、App session 解析和账号删除流程只依赖该接口，避免继续直接耦合
 * MyBatis Mapper。当前实现仍基于 MyBatis-Plus Mapper/ServiceImpl；后续拆分认证中心或替换
 * 存储时，可以只替换本接口实现。</p>
 */
public interface SysAuthDataService extends IService<SysUserEntity> {
    SysUserEntity userById(Long userId);
    boolean saveUser(SysUserEntity user);
    boolean updateUser(SysUserEntity user);

    SysUserIdentityEntity identityByProvider(String appCode, String providerCode, String providerSubject);
    boolean saveIdentity(SysUserIdentityEntity identity);
    boolean updateIdentity(SysUserIdentityEntity identity);
    int revokeIdentitiesByUser(String appCode, Long userId, OffsetDateTime now);

    SysAuthProviderTokenEntity providerTokenByIdentity(String appCode, String providerCode, String providerSubject);
    SysAuthProviderTokenEntity providerTokenByUserAndProvider(String appCode, Long userId, String providerCode);
    boolean saveProviderToken(SysAuthProviderTokenEntity token);
    boolean updateProviderToken(SysAuthProviderTokenEntity token);
    int countProviderTokens(String appCode, String providerCode);
    int countEncryptedRefreshTokens(String appCode, String providerCode);
    int countPlaintextRefreshTokenFallbacks(String appCode, String providerCode);
    int revokeProviderTokensByUser(String appCode, Long userId, OffsetDateTime now);

    boolean saveSession(SysAuthSessionEntity session);
    SysAuthSessionEntity activeSessionByTokenHash(String sessionTokenHash, OffsetDateTime now);
    int touchSessionLastSeen(Long sessionId, OffsetDateTime now);
    int revokeSession(Long sessionId, OffsetDateTime now);
    int revokeSessionsByUser(String appCode, Long userId, OffsetDateTime now);
}
