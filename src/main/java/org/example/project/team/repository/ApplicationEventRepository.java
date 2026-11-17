package org.example.project.team.repository;

import org.example.project.team.entity.ApplicationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {
    List<ApplicationEvent> findByApplicationIdOrderByIdAsc(Long applicationId);
}
