package com.apphub.backend.sys.auth.service.impl;

import com.apphub.backend.sys.auth.entity.SysAuthProviderTokenEntity;
import com.apphub.backend.sys.auth.entity.SysAuthSessionEntity;
import com.apphub.backend.sys.auth.entity.SysUserEntity;
import com.apphub.backend.sys.auth.entity.SysUserIdentityEntity;
import com.apphub.backend.sys.auth.mapper.SysAuthProviderTokenMapper;
import com.apphub.backend.sys.auth.mapper.SysAuthSessionMapper;
import com.apphub.backend.sys.auth.mapper.SysUserIdentityMapper;
import com.apphub.backend.sys.auth.mapper.SysUserMapper;
import com.apphub.backend.sys.auth.service.SysAuthDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class SysAuthDataServiceImpl extends ServiceImpl<SysUserMapper, SysUserEntity> implements SysAuthDataService {
    private final SysUserIdentityMapper userIdentityMapper;
    private final SysAuthProviderTokenMapper authProviderTokenMapper;
    private final SysAuthSessionMapper authSessionMapper;

    public SysAuthDataServiceImpl(
        SysUserIdentityMapper userIdentityMapper,
        SysAuthProviderTokenMapper authProviderTokenMapper,
        SysAuthSessionMapper authSessionMapper
    ) {
        this.userIdentityMapper = userIdentityMapper;
        this.authProviderTokenMapper = authProviderTokenMapper;
        this.authSessionMapper = authSessionMapper;
    }

    @Override
    public SysUserEntity userById(Long userId) {
        return userId == null ? null : baseMapper.selectById(userId);
    }

    @Override
    public boolean saveUser(SysUserEntity user) {
        return save(user);
    }

    @Override
    public boolean updateUser(SysUserEntity user) {
        return updateById(user);
    }

    @Override
    public SysUserIdentityEntity identityByProvider(String appCode, String providerCode, String providerSubject) {
        return userIdentityMapper.selectByProviderIdentity(appCode, providerCode, providerSubject);
    }

    @Override
    public boolean saveIdentity(SysUserIdentityEntity identity) {
        return userIdentityMapper.insert(identity) > 0;
    }

    @Override
    public boolean updateIdentity(SysUserIdentityEntity identity) {
        return userIdentityMapper.updateById(identity) > 0;
    }

    @Override
    public int revokeIdentitiesByUser(String appCode, Long userId, OffsetDateTime now) {
        return userIdentityMapper.revokeAllByUser(appCode, userId, now);
    }

    @Override
    public SysAuthProviderTokenEntity providerTokenByIdentity(String appCode, String providerCode, String providerSubject) {
        return authProviderTokenMapper.selectByProviderIdentity(appCode, providerCode, providerSubject);
    }

    @Override
    public SysAuthProviderTokenEntity providerTokenByUserAndProvider(String appCode, Long userId, String providerCode) {
        return authProviderTokenMapper.selectByUserAndProvider(appCode, userId, providerCode);
    }

    @Override
    public boolean saveProviderToken(SysAuthProviderTokenEntity token) {
        return authProviderTokenMapper.insert(token) > 0;
    }

    @Override
    public boolean updateProviderToken(SysAuthProviderTokenEntity token) {
        return authProviderTokenMapper.updateById(token) > 0;
    }

    @Override
    public int countProviderTokens(String appCode, String providerCode) {
        return authProviderTokenMapper.countByAppAndProvider(appCode, providerCode);
    }

    @Override
    public int countEncryptedRefreshTokens(String appCode, String providerCode) {
        return authProviderTokenMapper.countEncryptedRefreshTokens(appCode, providerCode);
    }

    @Override
    public int countPlaintextRefreshTokenFallbacks(String appCode, String providerCode) {
        return authProviderTokenMapper.countPlaintextRefreshTokenFallbacks(appCode, providerCode);
    }

    @Override
    public int revokeProviderTokensByUser(String appCode, Long userId, OffsetDateTime now) {
        return authProviderTokenMapper.revokeAllByUser(appCode, userId, now);
    }

    @Override
    public boolean saveSession(SysAuthSessionEntity session) {
        return authSessionMapper.insert(session) > 0;
    }

    @Override
    public SysAuthSessionEntity activeSessionByTokenHash(String sessionTokenHash, OffsetDateTime now) {
        return authSessionMapper.selectActiveByTokenHash(sessionTokenHash, now);
    }

    @Override
    public int touchSessionLastSeen(Long sessionId, OffsetDateTime now) {
        return authSessionMapper.touchLastSeen(sessionId, now);
    }

    @Override
    public int revokeSession(Long sessionId, OffsetDateTime now) {
        return authSessionMapper.revokeSession(sessionId, now);
    }

    @Override
    public int revokeSessionsByUser(String appCode, Long userId, OffsetDateTime now) {
        return authSessionMapper.revokeAllByUser(appCode, userId, now);
    }
}
