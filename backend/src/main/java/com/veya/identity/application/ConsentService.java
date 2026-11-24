package com.veya.identity.application;

import com.veya.identity.domain.ConsentDocument;
import com.veya.identity.domain.UserConsent;
import com.veya.identity.infrastructure.ConsentDocumentRepository;
import com.veya.identity.infrastructure.ConsentRepository;
import com.veya.shared.persistence.DatabaseErrors;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * LEG-2 / Q7: explicit, separately recorded and versioned consent. Withdrawal only records the time; what the
 * application does after the health-data consent is withdrawn is open (AQ-4) and is not implemented.
 */
@Service
public class ConsentService {

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    private final ConsentRepository consents;
    private final ConsentDocumentRepository documents;
    private final Clock clock;

    public ConsentService(ConsentRepository consents, ConsentDocumentRepository documents, Clock clock) {
        this.consents = consents;
        this.documents = documents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConsentDocument> currentDocuments() {
        return documents.findCurrent();
    }

    @Transactional(readOnly = true)
    public List<UserConsent> myConsents(UUID userId) {
        return consents.findByUserIdOrderByGrantedAtAscDocumentIdAsc(userId);
    }

    @Transactional
    public UserConsent grant(UUID userId, int documentId) {
        if (documentId > Short.MAX_VALUE) {
            throw ApiException.notFound();
        }
        ConsentDocument document = documents.findById((short) documentId).orElseThrow(ApiException::notFound);
        if (consents.existsByUserIdAndDocumentIdAndWithdrawnAtIsNull(userId, (short) documentId)) {
            throw alreadyActive(document);
        }
        try {
            UserConsent consent = consents.saveAndFlush(new UserConsent(userId, document));
            log.info("Consent granted userId={} document={} {}", userId, document.getConsentType(), document.getVersion());
            return consent;
        } catch (DataIntegrityViolationException ex) {
            if ("user_consent_active_uq".equals(DatabaseErrors.constraintName(ex))) {
                throw alreadyActive(document);
            }
            throw ex;
        }
    }

    /** AQ-2 (contract): unknown id -> 404, another user's record -> 403. */
    @Transactional
    public UserConsent withdraw(UUID userId, UUID consentId) {
        UserConsent consent = consents.findById(consentId).orElseThrow(ApiException::notFound);
        if (!consent.getUserId().equals(userId)) {
            throw ApiException.accessDenied();
        }
        if (!consent.isActive()) {
            throw new ApiException(ErrorCode.CONSENT_ALREADY_WITHDRAWN, "This consent has already been withdrawn.");
        }
        consent.withdraw(clock.instant());
        consents.flush();
        log.info("Consent withdrawn userId={} document={}", userId, consent.getDocument().getConsentType());
        return consent;
    }

    private static ApiException alreadyActive(ConsentDocument document) {
        return new ApiException(ErrorCode.CONSENT_ALREADY_ACTIVE,
                "Consent to " + document.getConsentType() + " " + document.getVersion() + " is already active.");
    }
}
