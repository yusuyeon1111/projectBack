package org.example.project.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.example.project.member.entity.Member;

import java.util.Date;

@Getter
@AllArgsConstructor
public class MemberResponse {
    private Long id;
    private String email;
    private String name;
    private String nickname;
    private String username;
    private Date createdAt;
    private String delYn;

    public MemberResponse(Member member) {
        this.id = member.getId();
        this.username = member.getUsername();
        this.name = member.getName();
        this.email = member.getEmail();
        this.nickname = member.getNickname();
        this.createdAt = member.getCreatedAt();
        this.delYn = member.getDelYn();
    }

}
