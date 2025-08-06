package org.example.project.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.stereotype.Service;

import java.util.Date;

@Entity
@Getter
@Service
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String postId;

    @Column
    private String memberId;

    @Column
    private char status;

    @Column
    private Date appliedAt;
}
