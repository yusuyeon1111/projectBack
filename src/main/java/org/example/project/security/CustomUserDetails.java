package org.example.project.security;

import org.example.project.member.entity.Member;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.stream.Collectors;

public class CustomUserDetails extends org.springframework.security.core.userdetails.User {
    private final String delYn;

    public CustomUserDetails(Member member) {
        super(member.getUsername(), member.getPassword(),
                member.getRoles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()));
        this.delYn = member.getDelYn();
    }

    public String getDelYn() {
        return delYn;
    }
}
