package com.apphub.backend.sys.compensation.controller;

import com.apphub.backend.common.filter.TraceFilter;
import com.apphub.backend.common.response.ApiResponse;
import com.apphub.backend.sys.compensation.model.CompensationCodeCreateRequest;
import com.apphub.backend.sys.compensation.model.CompensationCodeView;
import com.apphub.backend.sys.compensation.service.SysCompensationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * 后端补偿申请生成接口。
 *
 * <p>中文说明：该接口面向运营后台或 Postman 运维调用，调用方提交补偿事由、备注、权益标识、补偿次数和有效期，
 * 后端生成一个未使用的补偿码主记录。用户随后可在 App 家长区兑换该补偿码。</p>
 */
@Tag(name = "系统补偿申请", description = "后台生成补偿码和补偿记录的 API。")
@RestController
@RequestMapping("/api/v1/system/compensation-applications")
public class SysCompensationApplicationController {
    private final SysCompensationService compensationService;

    @Value("${backend.apps.paipai_readingcompanion.admin.configToken:}")
    private String adminConfigToken;

    public SysCompensationApplicationController(SysCompensationService compensationService) {
        this.compensationService = compensationService;
    }

    /**
     * 提交补偿申请并生成补偿码记录。
     *
     * @param appCode 应用编码，例如 paipai_readingcompanion。
     * @param token 后台管理 token，请求头 X-Admin-Config-Token。
     * @param body 补偿申请请求体。
     * @param httpServletRequest HTTP 请求对象，用于记录请求来源。
     * @return 生成后的补偿码记录。
     */
    @Operation(
        summary = "提交补偿申请并生成补偿记录",
        description = """
            后台或运营系统提交补偿申请信息，后端完成参数校验、管理 token 校验，并生成一个未使用的补偿码记录。
            当前接口生成 usage_credit 类型补偿，权益标识沿用现有系统标识：local_ocr、local_tts、cloud_ocr、cloud_tts。
            生成成功后返回补偿码、有效期、权益类型、次数和状态，用户可在 App 家长区兑换。
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "生成成功。",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject("""
                    {
                      "success": true,
                      "requestId": "a8f1b1e0-9f2f-4e47-bb4e-90a4b2f6d111",
                      "data": {
                        "id": 123,
                        "appCode": "paipai_readingcompanion",
                        "compensationCode": "PP-ABCDE-FGHJK-MNPQR",
                        "benefitType": "usage_credit",
                        "serviceType": "cloud_tts",
                        "grantCount": 10,
                        "grantValidDays": 30,
                        "grantValidUntilAt": "2026-06-15T00:00:00Z",
                        "claimScope": "multi_device_once",
                        "maxUses": 100,
                        "status": "unused",
                        "usedCount": 0
                      },
                      "message": null
                    }
                    """)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效，例如权益标识不支持、次数或有效期越界。"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "缺少管理 token、token 未配置或 token 无效。"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "appCode 对应应用不存在。"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "指定补偿码已存在。")
    })
    @PostMapping
    public ApiResponse<CompensationCodeView> generate(
        @Parameter(
            name = "appCode",
            in = ParameterIn.QUERY,
            required = true,
            description = "应用编码。拍拍伴读固定为 paipai_readingcompanion。",
            example = "paipai_readingcompanion"
        )
        @RequestParam String appCode,
        @Parameter(
            name = "X-Admin-Config-Token",
            in = ParameterIn.HEADER,
            required = true,
            description = "后台管理 token。",
            example = "7106e1405a154d068a166442dc773ad1ad10886a76144bc8ad6f4cbaa26354be"
        )
        @RequestHeader(value = "X-Admin-Config-Token", required = false) String token,
        @Valid @RequestBody CompensationApplicationRequest body,
        @Parameter(hidden = true) HttpServletRequest httpServletRequest
    ) {
        requireAdminToken(token);
        String normalizedAppCode = normalizeAppCode(appCode);
        if (normalizedAppCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "APP_CODE_REQUIRED");
        }

        // 中文说明：将补偿申请转换为现有补偿码创建模型，复用已实现的生成、唯一性、有效期和落库逻辑。
        CompensationCodeCreateRequest createRequest = new CompensationCodeCreateRequest(
            body.normalizedCompensationCode(),
            SysCompensationService.BENEFIT_USAGE_CREDIT,
            null,
            null,
            body.compensationCount(),
            body.validDays(),
            null,
            body.expiresAt(),
            body.normalizedClaimScope(),
            body.resolvedMaxUses(),
            body.normalizedBenefitKey(),
            buildNote(body, httpServletRequest)
        );
        CompensationCodeView generated = compensationService.createCode(normalizedAppCode, null, createRequest);
        return ApiResponse.success(currentRequestId(), generated);
    }

    /**
     * 校验后台管理 token。
     *
     * @param token 请求头 X-Admin-Config-Token 的值。
     */
    private void requireAdminToken(String token) {
        if (adminConfigToken == null || adminConfigToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_NOT_CONFIGURED");
        }
        if (token == null || !adminConfigToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN_CONFIG_TOKEN_INVALID");
        }
    }

    private String normalizeAppCode(String appCode) {
        if (appCode == null) {
            return null;
        }
        String trimmed = appCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 组装审计备注。
     *
     * <p>中文说明：备注会写入补偿码 metadata_json，用于后续客服、运营和数据库审计追踪。</p>
     */
    private String buildNote(CompensationApplicationRequest body, HttpServletRequest request) {
        String operator = operatorId(request);
        return "补偿事由：" + body.reason().trim()
            + "；备注：" + (body.remark() == null || body.remark().isBlank() ? "无" : body.remark().trim())
            + "；操作来源：" + operator;
    }

    private String operatorId(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "admin:" + forwardedFor.split(",")[0].trim();
        }
        return "admin:" + request.getRemoteAddr();
    }

    private String currentRequestId() {
        String requestId = MDC.get(TraceFilter.REQUEST_ID_MDC_KEY);
        return requestId == null || requestId.isBlank() ? "unknown" : requestId;
    }

    @Schema(description = "补偿申请生成请求。")
    public record CompensationApplicationRequest(
        @Schema(
            description = "可选：指定补偿码。为空时由后端生成。格式为 PP-ABCDE-FGHJK-MNPQR，也允许不带横线的 PPABCDEFGHIJKLMNO。",
            example = "PP-ABCDE-FGHJK-MNPQR"
        )
        @Pattern(regexp = "^(|PP(?:-?[A-Z2-9]{5}){3})$", message = "补偿码格式不正确")
        String compensationCode,

        @Schema(description = "补偿事由。用于审计和客服追踪。", example = "云端朗读服务异常补偿")
        @NotBlank(message = "补偿事由不能为空")
        @Size(max = 120, message = "补偿事由不能超过120个字符")
        String reason,

        @Schema(description = "备注信息。可填写工单号、客服记录号或补充说明。", example = "工单 TICKET-20260516-0001")
        @Size(max = 300, message = "备注不能超过300个字符")
        String remark,

        @Schema(
            description = "权益标识，沿用现有系统标识：local_ocr=本地拍读/识别，local_tts=本地朗读，cloud_ocr=云端OCR，cloud_tts=云端语音朗读。",
            example = "cloud_tts",
            allowableValues = {"local_ocr", "local_tts", "cloud_ocr", "cloud_tts"}
        )
        @NotBlank(message = "权益标识不能为空")
        @Pattern(regexp = "^(local_ocr|local_tts|cloud_ocr|cloud_tts)$", message = "权益标识仅支持 local_ocr、local_tts、cloud_ocr、cloud_tts")
        String benefitKey,

        @Schema(description = "补偿次数。最少1次，最多1000次。", example = "10")
        @Min(value = 1, message = "补偿次数至少为1")
        @Max(value = 1000, message = "补偿次数不能超过1000")
        Integer compensationCount,

        @Schema(description = "补偿权益有效期天数。最少1天，最多365天。", example = "30")
        @Min(value = 1, message = "有效期天数至少为1")
        @Max(value = 365, message = "有效期天数不能超过365")
        Integer validDays,

        @Schema(description = "补偿码过期时间。为空时默认按创建时间 + validDays 计算。", example = "2026-12-31T23:59:59Z")
        OffsetDateTime expiresAt,

        @Schema(
            description = "领取范围：single_use=只能绑定一次；multi_device_once=多个设备可各绑定一次。",
            example = "single_use",
            allowableValues = {"single_use", "multi_device_once"}
        )
        @Pattern(regexp = "^(|single_use|multi_device_once)$", message = "领取范围仅支持 single_use、multi_device_once")
        String claimScope,

        @Schema(description = "最大领取次数。single_use 固定为1；multi_device_once 表示最多可绑定的设备数。", example = "1")
        @Min(value = 1, message = "最大领取次数至少为1")
        @Max(value = 100000, message = "最大领取次数不能超过100000")
        Integer maxUses
    ) {
        @AssertTrue(message = "补偿次数不能为空")
        public boolean isCompensationCountPresent() {
            return compensationCount != null;
        }

        @AssertTrue(message = "有效期天数不能为空")
        public boolean isValidDaysPresent() {
            return validDays != null;
        }

        @AssertTrue(message = "多设备补偿码 maxUses 至少为2")
        public boolean isMultiDeviceMaxUsesValid() {
            return !"multi_device_once".equals(normalizedClaimScope()) || (maxUses != null && maxUses >= 2);
        }

        public String normalizedBenefitKey() {
            return benefitKey == null ? null : benefitKey.trim().toLowerCase(Locale.ROOT);
        }

        public String normalizedClaimScope() {
            if (claimScope == null || claimScope.isBlank()) {
                return maxUses != null && maxUses > 1
                    ? SysCompensationService.CLAIM_SCOPE_MULTI_DEVICE_ONCE
                    : SysCompensationService.CLAIM_SCOPE_SINGLE_USE;
            }
            return claimScope.trim().toLowerCase(Locale.ROOT);
        }

        public Integer resolvedMaxUses() {
            if (SysCompensationService.CLAIM_SCOPE_SINGLE_USE.equals(normalizedClaimScope())) {
                return 1;
            }
            return maxUses;
        }

        public String normalizedCompensationCode() {
            if (compensationCode == null || compensationCode.isBlank()) {
                return null;
            }
            return compensationCode.trim().toUpperCase(Locale.ROOT);
        }
    }
}
