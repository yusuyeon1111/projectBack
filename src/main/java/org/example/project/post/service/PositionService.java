package org.example.project.post.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.dto.ApplyMemeberDto;
import org.example.project.post.dto.ApplyPositionRequestDto;
import org.example.project.post.dto.ApplyResponseDto;
import org.example.project.post.dto.PostAcceptDto;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostPositionRepository;
import org.example.project.post.repositoty.PostRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PostPositionRepository postPositionRepository;
    private final PostPositionMemberRepository postPositionMemberRepository;

    // 포지션 id, username으로 신청 여부 판단
    public List<Long> getAppliedPositions(Long postId, String username) {
        List<PostPositionMember> appliedList = postPositionMemberRepository.findByMemberUsernameAndPostPosition_Post_Id(username, postId);
        return appliedList.stream()
                .map(ppm -> ppm.getPostPosition().getId())
                .collect(Collectors.toList());
    }

    // 포지션 신청. 동시 중복 신청은 DB 유니크 제약(uk_position_member)이 막는다.
    // save가 바로 커밋되어야 제약 위반을 여기서 잡을 수 있으므로 메서드 트랜잭션을 두지 않는다.
    public ResponseEntity<String> applyPostPosition(ApplyPositionRequestDto dto) {
        PostPosition position = postPositionRepository.findById(dto.getPositionId())
                .orElseThrow(()-> new RuntimeException("포지션이 존재하지 않습니다."));
        if ("END".equals(position.getStatus())) {
            return ResponseEntity.badRequest().body("모집이 마감된 포지션입니다.");
        }
        Member member = memberRepository.findByUsername(dto.getUsername())
                .orElseThrow(()-> new RuntimeException("사용자가 존재하지 않습니다."));
        boolean alreadyApplied = postPositionMemberRepository.existsByPostPositionAndMember(position, member);
        if(alreadyApplied) {
            return ResponseEntity.badRequest().body("이미 신청한 포지션입니다.");
        }

        PostPositionMember postPositionMember = new PostPositionMember();
        postPositionMember.setPostPosition(position);
        postPositionMember.setMember(member);
        postPositionMember.setStatus("APPLY");
        postPositionMember.setAppliedAt(LocalDateTime.now());
        try {
            postPositionMemberRepository.save(postPositionMember);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body("이미 신청한 포지션입니다.");
        }

        return ResponseEntity.ok("포지션 신청이 완료되었습니다.");
    }

    // 신청 기록을 찾고, 그 신청이 달린 글의 작성자인지 확인. 글/포지션은 클라이언트가 보낸 id가 아닌 신청 기록에서 꺼낸다.
    // 글 행 잠금을 트랜잭션의 첫 조회로 둔다. MySQL(REPEATABLE READ)은 첫 일반 조회 시점의 스냅샷을 계속 읽으므로,
    // 잠금보다 먼저 조회하면 앞선 트랜잭션이 커밋한 승인 수를 못 보고 정원을 넘겨 승인하게 된다.
    private PostPositionMember lockAndFindOwnedApplication(Long postPositionMemberId, String username) {
        postRepository.findByApplicationIdForUpdate(postPositionMemberId)
                .orElseThrow(()-> new RuntimeException("신청 내역이 존재하지 않습니다."));
        PostPositionMember application = postPositionMemberRepository.findById(postPositionMemberId)
                .orElseThrow(()-> new RuntimeException("신청 내역이 존재하지 않습니다."));
        if (!application.getPostPosition().getPost().getAuthor().equals(username)) {
            throw new AccessDeniedException("본인이 작성한 글의 신청만 처리할 수 있습니다.");
        }
        if (!"APPLY".equals(application.getStatus())) {
            throw new IllegalStateException("이미 처리된 신청입니다.");
        }
        return application;
    }

    // 포지션 수락
    @Transactional
    public List<ApplyMemeberDto> acceptPosition(ApplyPositionRequestDto dto, String username) {
        PostPositionMember position = lockAndFindOwnedApplication(dto.getPostPositionId(), username);
        PostPosition postPosition = position.getPostPosition();
        Post post = postPosition.getPost();

        long acceptedCount = postPositionMemberRepository.findCountByPostPositionId(postPosition.getId());
        if (acceptedCount >= postPosition.getCount()) {
            throw new IllegalStateException("모집 인원이 모두 찼습니다.");
        }
        position.setStatus("ACCEPT");
        if (acceptedCount + 1 == postPosition.getCount()) {
            postPosition.setStatus("END");
            postPositionRepository.save(postPosition);
        }
        long endCount = postPositionRepository.countByPostIdAndStatus(post.getId(), "END");
        long totalCount = postPositionRepository.countByPostId(post.getId());
        if(endCount == totalCount) {
            post.setStatus("END");
            postRepository.save(post);
        }
        position.setAcceptedAt(LocalDateTime.now());
        postPositionMemberRepository.save(position);

        List<ApplyMemeberDto> list = postPositionMemberRepository.findPostPositionMemberByPostId(post.getId());
        return list;
    }

    // 포지션 거절
    @Transactional
    public List<ApplyMemeberDto>  rejectPosition(ApplyPositionRequestDto dto, String username) {
        PostPositionMember position = lockAndFindOwnedApplication(dto.getPostPositionId(), username);

        position.setStatus("REJECT");
        position.setRejectedAt(LocalDateTime.now());
        postPositionMemberRepository.save(position);
        List<ApplyMemeberDto> list = postPositionMemberRepository.findPostPositionMemberByPostId(position.getPostPosition().getPost().getId());
        return list;
    }

    // username으로 신청한 포지션 리스트 조회
    public List<ApplyResponseDto> findApplyPost(String username) {
        List<ApplyResponseDto> apply = postPositionMemberRepository.findAllByMemberUsername(username);
        if (apply.isEmpty()) {
            return new ArrayList<>();
        }
        return apply;
    }

    // 참여중인 프로젝트 조회
    public List<PostAcceptDto> findAcceptPost(String username) {
        return postPositionMemberRepository.findAcceptedPostsByUsername(username);
    }

    // 포지션 조회
    public List<ApplyResponseDto> getApplyPositionList(Long postId) {
        return postPositionMemberRepository.findByPostId(postId).stream()
                .map(arr -> {
                    PostPositionMember ppm = (PostPositionMember) arr[0];
                    Member m = (Member) arr[1];
                    PostPosition pp = ppm.getPostPosition();
                    Post p = pp.getPost();
                    return new ApplyResponseDto(
                            ppm.getId(),
                            m.getId(),
                            m.getName(),
                            pp.getId(),
                            pp.getRole(),
                            pp.getStatus(),
                            ppm.getStatus(),
                            p.getId(),
                            p.getTitle(),
                            p.getCategory(),
                            ppm.getAppliedAt(),
                            ppm.getAcceptedAt(),
                            ppm.getRejectedAt()
                    );
                })
                .toList();
    }
}
