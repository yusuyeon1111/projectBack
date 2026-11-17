package org.example.project.team.dto;

import java.time.LocalDate;

// 등록·수정 공용. 수정은 부분 수정이 아니라 고칠 수 있는 칸 전체를 바꾸고, 읽을 때 받은 version을 함께 보낸다
public record TaskRequest(String title, String description, Long assigneeMemberId, LocalDate dueDate, Long version) {}
