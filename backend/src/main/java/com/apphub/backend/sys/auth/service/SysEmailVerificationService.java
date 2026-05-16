package com.apphub.backend.sys.auth.service;

import com.apphub.backend.sys.auth.entity.SysEmailVerificationTicketEntity;
import com.apphub.backend.sys.auth.service.crud.SysEmailVerificationTicketCrudService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
public class SysEmailVerificationService {
    private static final Logger log = LoggerFactory.getLogger(SysEmailVerificationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysEmailVerificationTicketCrudService ticketCrudService;
    private final SessionTokenHashService hashService;
    private final ObjectMapper objectMapper;

    @Value("${backend.environment:dev}")
    private String backendEnvironment;

    public SysEmailVerificationService(
        SysEmailVerificationTicketCrudService ticketCrudService,
        SessionTokenHashService hashService,
        ObjectMapper objectMapper
    ) {
        this.ticketCrudService = ticketCrudService;
        this.hashService = hashService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EmailVerificationTicketView requestCode(String appCode, String email, String sceneCode, String requestIp, Map<String, Object> payload) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedScene = normalizeScene(sceneCode);
        String emailKey = emailKey(normalizedEmail);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ticketCrudService.expirePending(appCode, emailKey, normalizedScene, now);

        String code = generateCode();
        SysEmailVerificationTicketEntity entity = new SysEmailVerificationTicketEntity();
        entity.setAppCode(appCode);
        entity.setEmail(emailKey);
        entity.setSceneCode(normalizedScene);
        entity.setCodeHash(hashService.hash(code));
        entity.setStatus("pending");
        entity.setAttemptCount(0);
        entity.setMaxAttemptCount(6);
        entity.setExpiresAt(now.plusMinutes(10));
        entity.setRequestIp(blankToNull(requestIp));
        entity.setPayloadJson(toJson(payload));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        ticketCrudService.save(entity);

        log.info("email verification code generated appCode={} scene={} emailKeyPrefix={} ticketId={}", appCode, normalizedScene, emailKey.substring(0, Math.min(12, emailKey.length())), entity.getId());
        return new EmailVerificationTicketView(
            maskEmail(normalizedEmail),
            normalizedScene,
            entity.getExpiresAt().toString(),
            isDebugEnvironment() ? "logged_only" : "not_configured",
            isDebugEnvironment() ? code : null,
            isDebugEnvironment() ? "当前环境未接入真实邮件发送，验证码已记录到日志并回显 debugCode 便于联调。" : "当前环境尚未配置真实邮件发送器。"
        );
    }

    @Transactional
    public ConsumedVerificationTicket consumeCode(String appCode, String email, String sceneCode, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedScene = normalizeScene(sceneCode);
        String emailKey = emailKey(normalizedEmail);
        SysEmailVerificationTicketEntity entity = ticketCrudService.selectLatest(appCode, emailKey, normalizedScene);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_TICKET_NOT_FOUND");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!"pending".equalsIgnoreCase(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_TICKET_NOT_PENDING");
        }
        if (entity.getExpiresAt() == null || !entity.getExpiresAt().isAfter(now)) {
            entity.setStatus("expired");
            entity.setUpdatedAt(now);
            ticketCrudService.updateById(entity);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_EXPIRED");
        }
        String codeHash = hashService.hash(requireCode(code));
        if (!codeHash.equals(entity.getCodeHash())) {
            entity.setAttemptCount((entity.getAttemptCount() == null ? 0 : entity.getAttemptCount()) + 1);
            if (entity.getAttemptCount() >= (entity.getMaxAttemptCount() == null ? 6 : entity.getMaxAttemptCount())) {
                entity.setStatus("expired");
            }
            entity.setUpdatedAt(now);
            ticketCrudService.updateById(entity);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_INVALID");
        }
        entity.setStatus("consumed");
        entity.setVerifiedAt(now);
        entity.setConsumedAt(now);
        entity.setUpdatedAt(now);
        ticketCrudService.updateById(entity);
        return new ConsumedVerificationTicket(maskEmail(normalizedEmail), entity.getSceneCode(), now.toString(), entity.getId());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMAIL_REQUIRED");
        }
        return email.trim().toLowerCase();
    }

    private String normalizeScene(String sceneCode) {
        if (sceneCode == null || sceneCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SCENE_CODE_REQUIRED");
        }
        return sceneCode.trim().toLowerCase();
    }

    private String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VERIFICATION_CODE_REQUIRED");
        }
        return code.trim();
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String emailKey(String normalizedEmail) {
        return hashService.hash("email-verification:" + normalizedEmail);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(at - 1);
    }

    private boolean isDebugEnvironment() {
        return backendEnvironment == null || !"prod".equalsIgnoreCase(backendEnvironment.trim());
    }

    public record EmailVerificationTicketView(
        String maskedEmail,
        String sceneCode,
        String expiresAt,
        String deliveryStatus,
        String debugCode,
        String note
    ) {}

    public record ConsumedVerificationTicket(
        String email,
        String sceneCode,
        String consumedAt,
        Long ticketId
    ) {}
}
