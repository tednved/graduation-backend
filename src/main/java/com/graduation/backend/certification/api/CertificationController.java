package com.graduation.backend.certification.api;

import com.graduation.backend.certification.api.dto.CertificationLatestResponse;
import com.graduation.backend.certification.api.dto.CertificationResponse;
import com.graduation.backend.certification.api.dto.SubmitCertificationRequest;
import com.graduation.backend.certification.application.CertificationService;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 本人认证申请。审核侧见 {@link AdminCertificationController}。 */
@RestController
@RequestMapping("/api/v1/certifications")
public class CertificationController {

    private final CertificationService certificationService;
    private final CurrentUserService currentUserService;

    public CertificationController(CertificationService certificationService,
                                   CurrentUserService currentUserService) {
        this.certificationService = certificationService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CertificationResponse> submit(@Valid @RequestBody SubmitCertificationRequest request) {
        return ApiResponse.ok(certificationService.submit(currentUserService.requireCurrentUserId(), request));
    }

    @GetMapping("/me/latest")
    public ApiResponse<CertificationLatestResponse> latest() {
        return ApiResponse.ok(certificationService.latest(currentUserService.requireCurrentUserId()));
    }
}
