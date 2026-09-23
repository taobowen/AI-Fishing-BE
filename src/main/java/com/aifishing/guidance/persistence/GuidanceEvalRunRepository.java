package com.aifishing.guidance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GuidanceEvalRunRepository extends JpaRepository<GuidanceEvalRunEntity, UUID> {
}
