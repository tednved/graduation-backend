package com.graduation.backend.certification.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.certification.api.dto.CertificationLatestResponse;
import com.graduation.backend.certification.api.dto.CertificationResponse;
import com.graduation.backend.certification.api.dto.SubmitCertificationRequest;
import com.graduation.backend.certification.domain.Certification;
import com.graduation.backend.certification.domain.CertificationRepository;
import com.graduation.backend.certification.query.CertificationListRow;
import com.graduation.backend.certification.query.CertificationQueryMapper;
import com.graduation.backend.common.audit.AuditLogService;
import com.graduation.backend.common.domain.Campus;
import com.graduation.backend.common.domain.CampusRepository;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.domain.CertificationType;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.common.web.dto.CampusRefResponse;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 校园认证的读写边界。
 *
 * <p>「同一用户最多一个 PENDING」通过先锁用户行再检查实现：单纯对 {@code certifications} 做
 * {@code SELECT ... FOR UPDATE} 在「尚无 PENDING」时锁不到任何行，两个并发提交仍会各插入一条。
 * 明细列表走 MyBatis-Plus 物理分页，单条审核走 JPA，两者不会同时更新同一行。
 */
@Service
public class CertificationService {

    private final CertificationRepository certificationRepository;
    private final CertificationQueryMapper certificationQueryMapper;
    private final CampusRepository campusRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public CertificationService(CertificationRepository certificationRepository,
                                CertificationQueryMapper certificationQueryMapper,
                                CampusRepository campusRepository,
                                UserService userService,
                                AuditLogService auditLogService,
                                Clock clock) {
        this.certificationRepository = certificationRepository;
        this.certificationQueryMapper = certificationQueryMapper;
        this.campusRepository = campusRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public CertificationResponse submit(Long userId, SubmitCertificationRequest request) {
        if (request.type() != CertificationType.MANUAL) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前只支持人工审核（MANUAL）认证");
        }
        Instant now = clock.instant();
        User user = userService.requireActiveUserForUpdate(userId);

        if (certificationRepository
                .findFirstByUserIdAndStatusOrderByIdDesc(userId, CertificationStatus.PENDING)
                .isPresent()) {
            throw new BusinessException(ErrorCode.CERTIFICATION_PENDING_EXISTS, "已有待审核的认证申请，请等待审核结果");
        }

        Long campusId = user.getCampus() == null ? mainCampusId() : user.getCampus().getId();
        Certification saved = certificationRepository.save(Certification.submit(
                userId,
                campusId,
                CertificationType.MANUAL,
                CertificationMasker.maskRealName(request.realName()),
                CertificationMasker.maskStudentNo(request.studentNo()),
                now));
        user.changeCertificationStatus(CertificationStatus.PENDING, now);
        return CertificationResponse.from(saved, campusRef(campusId));
    }

    @Transactional(readOnly = true)
    public CertificationLatestResponse latest(Long userId) {
        userService.requireActiveUser(userId);
        return certificationRepository.findFirstByUserIdOrderByIdDesc(userId)
                .map(certification -> new CertificationLatestResponse(
                        certification.getStatus(),
                        CertificationResponse.from(certification, campusRef(certification.getCampusId()))))
                .orElseGet(CertificationLatestResponse::notSubmitted);
    }

    @Transactional(readOnly = true)
    public PageResult<CertificationResponse> adminList(int page, int size, CertificationStatus status) {
        Page<CertificationListRow> query = new Page<>(page + 1L, size);
        IPage<CertificationListRow> result = certificationQueryMapper.selectPage(
                query, status == null ? null : status.name());
        Map<Long, CampusRefResponse> campuses = campusRefs();
        List<CertificationResponse> items = result.getRecords().stream()
                .map(row -> toResponse(row, campuses))
                .toList();
        return PageResult.of(page, size, result.getTotal(), items);
    }

    @Transactional
    public CertificationResponse approve(Long operatorId, Long certificationId) {
        Certification certification = lockPending(certificationId);
        Instant now = clock.instant();
        certification.approve(operatorId, now);
        userService.requireUser(certification.getUserId())
                .changeCertificationStatus(CertificationStatus.APPROVED, now);
        auditLogService.record(operatorId, "CERTIFICATION_APPROVE", "CERTIFICATION", certification.getId(),
                Map.of("status", CertificationStatus.APPROVED.name()));
        return CertificationResponse.from(certification, campusRef(certification.getCampusId()));
    }

    @Transactional
    public CertificationResponse reject(Long operatorId, Long certificationId, String reason) {
        Certification certification = lockPending(certificationId);
        Instant now = clock.instant();
        certification.reject(operatorId, reason, now);
        userService.requireUser(certification.getUserId())
                .changeCertificationStatus(CertificationStatus.REJECTED, now);
        auditLogService.record(operatorId, "CERTIFICATION_REJECT", "CERTIFICATION", certification.getId(),
                Map.of("status", CertificationStatus.REJECTED.name()));
        return CertificationResponse.from(certification, campusRef(certification.getCampusId()));
    }

    private Certification lockPending(Long certificationId) {
        return certificationRepository.findByIdForUpdate(certificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "认证申请不存在"));
    }

    private CertificationResponse toResponse(CertificationListRow row, Map<Long, CampusRefResponse> campuses) {
        CampusRefResponse campus = campuses.get(row.getCampusId());
        return new CertificationResponse(
                row.getId(),
                row.getUserId(),
                campus,
                CertificationType.valueOf(row.getType()),
                row.getRealNameMasked(),
                row.getStudentNoMasked(),
                CertificationStatus.valueOf(row.getStatus()),
                row.getRejectReason(),
                row.getReviewedAt(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private Map<Long, CampusRefResponse> campusRefs() {
        return campusRepository.findAll().stream()
                .collect(Collectors.toMap(Campus::getId, CampusRefResponse::from,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private CampusRefResponse campusRef(Long campusId) {
        return campusRepository.findById(campusId)
                .map(CampusRefResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "认证申请关联的校区不存在"));
    }

    private Long mainCampusId() {
        return campusRepository.findByCode(Campus.MAIN_CODE)
                .map(Campus::getId)
                .orElseThrow(() -> new IllegalStateException("缺少 code=MAIN 的校区种子数据，请检查 V5 迁移"));
    }
}
