package com.aifishing.guidance.learning;

import com.aifishing.AbstractIntegrationTest;
import com.aifishing.guidance.contracts.LearningJobType;
import com.aifishing.guidance.contracts.LearningQuarantineReason;
import com.aifishing.guidance.control.AgentRuntimeControlPatch;
import com.aifishing.guidance.control.AgentRuntimeControlStore;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxEntity;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxRepository;
import com.aifishing.guidance.persistence.GuidanceLearningOutboxStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GuidanceLearningOutboxIT extends AbstractIntegrationTest {

    private static final String UNKNOWN_TYPE = "NOT_A_REAL_JOB";

    @Autowired
    private GuidanceLearningOutboxClaimer claimer;

    @Autowired
    private GuidanceLearningOutboxService outboxService;

    @Autowired
    private GuidanceLearningOutboxRepository outboxRepository;

    @Autowired
    private AgentRuntimeControlStore runtimeControlStore;

    @AfterEach
    void restoreJobTypeCheck() {
        jdbcTemplate.update("delete from guidance_learning_outbox where job_type = ?", UNKNOWN_TYPE);
        jdbcTemplate.execute("alter table guidance_learning_outbox drop constraint if exists guidance_learning_outbox_job_type_check");
        jdbcTemplate.execute("""
                alter table guidance_learning_outbox
                add constraint guidance_learning_outbox_job_type_check check (job_type in (
                    'ATTRIBUTE_OUTCOME', 'AGGREGATE_EMPIRICAL', 'SESSION_SUMMARY',
                    'REFLECTION_EVAL', 'PREFERENCE_UPDATE', 'ONLINE_METRICS_ROLLUP'
                ))
                """);
    }

    @Test
    void unknownJobTypeIsQuarantinedNotPendingOrDone() {
        UUID id = insertUnknownJob(GuidanceLearningOutboxStatus.PENDING, Instant.now(), null);

        assertThat(claimer.drain()).isEqualTo(1);

        assertThat(status(id)).isEqualTo("QUARANTINED");
        assertThat(quarantineReason(id)).isEqualTo(LearningQuarantineReason.UNKNOWN_JOB_TYPE.name());
        assertThat(status(id)).isNotEqualTo("PENDING");
        assertThat(status(id)).isNotEqualTo("DONE");
        assertThat(attemptCount(id)).isZero();
    }

    @Test
    void staleClaimedUnknownJobIsReclaimedAndQuarantined() {
        UUID id = insertUnknownJob(
                GuidanceLearningOutboxStatus.CLAIMED,
                Instant.parse("2026-09-16T12:00:00Z"),
                UUID.randomUUID()
        );

        assertThat(claimer.drain()).isEqualTo(1);

        assertThat(status(id)).isEqualTo("QUARANTINED");
        assertThat(quarantineReason(id)).isEqualTo(LearningQuarantineReason.UNKNOWN_JOB_TYPE.name());
        assertThat(claimToken(id)).isNull();
    }

    @Test
    void transientFailureRetriesThenMovesToDlq() {
        UUID id = insertJob(
                LearningJobType.ATTRIBUTE_OUTCOME.name(),
                GuidanceLearningOutboxStatus.PENDING,
                0,
                2,
                Instant.now(),
                null
        );

        assertThat(claimer.drain()).isEqualTo(1);
        entityManager.clear();
        GuidanceLearningOutboxEntity retried = outboxRepository.findById(id).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(GuidanceLearningOutboxStatus.PENDING);
        assertThat(retried.getAttemptCount()).isEqualTo(1);
        assertThat(retried.getLastError()).contains("ATTRIBUTE_OUTCOME requires fishingSessionId");

        jdbcTemplate.update(
                "update guidance_learning_outbox set available_at = now() - interval '1 second' where id = ?",
                id
        );
        entityManager.clear();

        assertThat(claimer.drain()).isEqualTo(1);
        entityManager.clear();
        GuidanceLearningOutboxEntity dlq = outboxRepository.findById(id).orElseThrow();
        assertThat(dlq.getStatus()).isEqualTo(GuidanceLearningOutboxStatus.DLQ);
        assertThat(dlq.getAttemptCount()).isEqualTo(2);
        assertThat(dlq.getQuarantineReason()).isNull();
    }

    @Test
    void enqueueIsIdempotentOnKey() {
        GuidanceLearningOutboxEntity first = outboxService.enqueue(
                null,
                LearningJobType.SESSION_SUMMARY,
                "learning-idempotent-key",
                Map.of("n", 1)
        );
        GuidanceLearningOutboxEntity second = outboxService.enqueue(
                null,
                LearningJobType.SESSION_SUMMARY,
                "learning-idempotent-key",
                Map.of("n", 2)
        );

        assertThat(second.getId()).isEqualTo(first.getId());
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from guidance_learning_outbox where idempotency_key = ?",
                Integer.class,
                "learning-idempotent-key"
        );
        assertThat(rows).isEqualTo(1);
        entityManager.clear();
        GuidanceLearningOutboxEntity stored = outboxRepository.findById(first.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GuidanceLearningOutboxStatus.PENDING);
        assertThat(((Number) stored.getPayload().get("n")).intValue()).isEqualTo(1);
    }

    @Test
    void learningDisabledLeavesMutationPendingAndStillClaimsObservation() {
        runtimeControlStore.update(new AgentRuntimeControlPatch(null, null, null, null, false));
        UUID observation = insertJob(
                LearningJobType.ATTRIBUTE_OUTCOME.name(),
                GuidanceLearningOutboxStatus.PENDING,
                0,
                8,
                Instant.now().minusSeconds(1),
                null
        );
        UUID mutation = insertJob(
                LearningJobType.AGGREGATE_EMPIRICAL.name(),
                GuidanceLearningOutboxStatus.PENDING,
                0,
                8,
                Instant.now().minusSeconds(1),
                null
        );
        UUID metrics = insertJob(
                LearningJobType.ONLINE_METRICS_ROLLUP.name(),
                GuidanceLearningOutboxStatus.PENDING,
                0,
                8,
                Instant.now().minusSeconds(1),
                null
        );
        entityManager.clear();

        int processed = claimer.drain();
        entityManager.clear();

        assertThat(processed).isGreaterThanOrEqualTo(1);
        assertThat(status(mutation)).isEqualTo("PENDING");
        assertThat(attemptCount(mutation)).isZero();
        assertThat(claimToken(mutation)).isNull();
        assertThat(attemptCount(observation)).isGreaterThan(0);
        assertThat(status(metrics)).isIn("DONE", "PENDING", "DLQ");
        if ("PENDING".equals(status(metrics))) {
            assertThat(attemptCount(metrics)).isGreaterThan(0);
        }
    }

    private UUID insertUnknownJob(GuidanceLearningOutboxStatus status, Instant at, UUID claimToken) {
        jdbcTemplate.execute("alter table guidance_learning_outbox drop constraint if exists guidance_learning_outbox_job_type_check");
        return insertJob(UNKNOWN_TYPE, status, 0, 8, at, claimToken);
    }

    private UUID insertJob(
            String jobType,
            GuidanceLearningOutboxStatus status,
            int attemptCount,
            int maxAttempts,
            Instant availableAt,
            UUID claimToken
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into guidance_learning_outbox (
                    id, job_type, idempotency_key, payload, status,
                    attempt_count, max_attempts, available_at, created_at, claimed_at, claim_token
                ) values (?, ?, ?, '{}'::jsonb, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                jobType,
                "learning-it-" + id,
                status.name(),
                attemptCount,
                maxAttempts,
                Timestamp.from(availableAt),
                Timestamp.from(availableAt),
                status == GuidanceLearningOutboxStatus.CLAIMED ? Timestamp.from(availableAt) : null,
                claimToken
        );
        return id;
    }

    private String status(UUID id) {
        return jdbcTemplate.queryForObject(
                "select status from guidance_learning_outbox where id = ?",
                String.class,
                id
        );
    }

    private String quarantineReason(UUID id) {
        return jdbcTemplate.queryForObject(
                "select quarantine_reason from guidance_learning_outbox where id = ?",
                String.class,
                id
        );
    }

    private UUID claimToken(UUID id) {
        return jdbcTemplate.queryForObject(
                "select claim_token from guidance_learning_outbox where id = ?",
                UUID.class,
                id
        );
    }

    private int attemptCount(UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "select attempt_count from guidance_learning_outbox where id = ?",
                Integer.class,
                id
        );
        return count == null ? 0 : count;
    }
}
