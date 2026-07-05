package com.example.HealthAssistBackend.repository;

import com.example.HealthAssistBackend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByWorkflowIdOrderByStepNumberAsc(String workflowId);

    List<AuditLog> findByAgentName(String agentName);

    List<AuditLog> findByEscalatedTrue();

    List<AuditLog> findByRiskTier(String riskTier);

    List<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<AuditLog> findByPiiDetectedTrue();

    List<AuditLog> findByModerationFlaggedTrue();

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    @Query("SELECT DISTINCT a.workflowId FROM AuditLog a ORDER BY a.workflowId DESC")
    List<String> findDistinctWorkflowIds();
}
