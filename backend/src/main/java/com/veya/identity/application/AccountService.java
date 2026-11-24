package com.veya.identity.application;

import com.veya.identity.domain.AppUser;
import com.veya.identity.domain.ConsentDocument;
import com.veya.identity.domain.UserConsent;
import com.veya.identity.infrastructure.ConsentDocumentRepository;
import com.veya.identity.infrastructure.ConsentRepository;
import com.veya.identity.infrastructure.UserDataExport;
import com.veya.identity.infrastructure.UserRepository;
import com.veya.identity.infrastructure.security.TokenService;
import com.veya.identity.infrastructure.security.TokenService.IssuedToken;
import com.veya.shared.persistence.DatabaseErrors;
import com.veya.shared.web.ApiException;
import com.veya.shared.web.ErrorCode;
import com.veya.shared.web.FieldViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registration, sign-in, account, export and deletion (US-01, US-04, US-30, LEG-2, LEG-4, Q6-Q8, Q13). */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    /** bcrypt only uses the first 72 bytes; longer passwords are rejected instead of being silently truncated. */
    static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final ConsentRepository consents;
    private final ConsentDocumentRepository documents;
    private final UserDataExport export;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;
    /** Compared against when the e-mail is unknown, so both failure cases cost the same time (no account probing). */
    private final String timingDummyHash;

    public AccountService(UserRepository users, ConsentRepository consents, ConsentDocumentRepository documents,
                          UserDataExport export, PasswordEncoder passwordEncoder, TokenService tokens, Clock clock) {
        this.users = users;
        this.consents = consents;
        this.documents = documents;
        this.export = export;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * US-01: creates the account with a bcrypt hash, stores the adult confirmation time (Q8, Q13) and one consent
     * record per accepted document (LEG-2, Q7). AQ-4 (PROPOSED in the contract): every current consent document
     * must be accepted. Does not sign the user in.
     */
    @Transactional
    public AppUser register(String email, String password, List<Integer> consentDocumentIds) {
        Map<Integer, ConsentDocument> current = documents.findCurrent().stream()
                .collect(Collectors.toMap(ConsentDocument::getId, Function.identity()));
        List<FieldViolation> errors = validateRegistration(password, consentDocumentIds, current.keySet());
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw emailTaken();
        }
        AppUser user;
        try {
            user = users.saveAndFlush(new AppUser(email, passwordEncoder.encode(password), clock.instant()));
        } catch (DataIntegrityViolationException ex) { // concurrent registration with the same e-mail
            if ("app_user_email_uq".equals(DatabaseErrors.constraintName(ex))) {
                throw emailTaken();
            }
            throw ex;
        }
        for (Integer documentId : consentDocumentIds) {
            consents.save(new UserConsent(user.getId(), current.get(documentId)));
        }
        consents.flush();
        log.info("Account registered userId={} consents={}", user.getId(), consentDocumentIds.size());
        return user;
    }

    /** Pure validation of a registration request (unit-tested in AccountServiceTests). */
    static List<FieldViolation> validateRegistration(String password, List<Integer> consentDocumentIds,
                                                     Set<Integer> currentDocumentIds) {
        List<FieldViolation> errors = new ArrayList<>();
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            errors.add(FieldViolation.of("password", "must not be longer than 72 bytes"));
        }
        if (new HashSet<>(consentDocumentIds).size() != consentDocumentIds.size()) {
            errors.add(FieldViolation.of("consentDocumentIds", "must not contain duplicates"));
        }
        for (int i = 0; i < consentDocumentIds.size(); i++) {
            if (!currentDocumentIds.contains(consentDocumentIds.get(i))) {
                errors.add(new FieldViolation("consentDocumentIds[" + i + "]", "is not a current consent document",
                        consentDocumentIds.get(i)));
            }
        }
        if (!consentDocumentIds.containsAll(currentDocumentIds)) {
            errors.add(FieldViolation.of("consentDocumentIds",
                    "must accept all current consent documents " + currentDocumentIds.stream().sorted().toList()));
        }
        return errors;
    }

    /** US-01: the same 401 for an unknown e-mail and a wrong password. */
    @Transactional(readOnly = true)
    public IssuedToken login(String email, String password) {
        Optional<AppUser> user = users.findByEmailIgnoreCase(email);
        if (user.isEmpty()) {
            passwordEncoder.matches(password, timingDummyHash); // same cost as a real check
            log.info("Sign-in rejected (unknown account or wrong password)");
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.get().getPasswordHash())) {
            log.info("Sign-in rejected (unknown account or wrong password)");
            throw invalidCredentials();
        }
        log.info("Signed in userId={}", user.get().getId());
        return tokens.issue(user.get().getId());
    }

    @Transactional(readOnly = true)
    public AppUser account(UUID userId) {
        return users.findById(userId).orElseThrow(() ->
                new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "A valid bearer token is required."));
    }

    /** US-04: deletes the account; ON DELETE CASCADE removes all personal records in the same statement. */
    @Transactional
    public void delete(UUID userId) {
        users.deleteById(userId);
        users.flush();
        log.info("Account deleted userId={}", userId);
    }

    /** LEG-4 / Q6: minimal JSON export of the caller's own records. */
    @Transactional(readOnly = true)
    public String export(UUID userId) {
        String json = export.exportJson(userId);
        if (json == null) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "A valid bearer token is required.");
        }
        log.info("Data exported userId={}", userId);
        return json;
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "E-mail or password is incorrect.");
    }

    private static ApiException emailTaken() {
        return new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this e-mail already exists.");
    }
}
