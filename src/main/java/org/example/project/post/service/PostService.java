package org.example.project.post.service;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project.admin.dto.MonthlyPostDto;
import org.example.project.admin.dto.MonthlyStatsDto;
import org.example.project.admin.dto.PostChartResponse;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.dto.*;
import org.example.project.post.entity.*;
import org.example.project.post.repositoty.PostLikeRepository;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostPositionRepository;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.post.repositoty.PostSearchSpec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class PostService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PostPositionRepository postPositionRepository;
    private final PostPositionMemberRepository postPositionMemberRepository;
    private final PostLikeRepository postLikeRepository;
    private final EntityManager entityManager;

    private static final int PAGES_AHEAD = 10;

    // 게시글 작성
    @Transactional
    public Long createPost(PostRequestDto dto) {
        Post post = new Post();
        String nickname = memberRepository.findNicknameByUsername(dto.getAuthor())
                .orElseThrow(() -> new IllegalArgumentException("사용자 닉네임을 찾을 수 없습니다."));
        post.setNickname(nickname);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setAuthor(dto.getAuthor());
        post.setProjectType(dto.getProjectType());
        post.setRegion(dto.getRegion());
        post.setStatus(dto.getStatus());
        post.setCreated(new Date());
        post.setCategory(dto.getCategory());
        post.setStartDate(dto.getStartDate());
        post.setEndDate(dto.getEndDate());
        post.setPostStatus(dto.getPostStatus());
        if(dto.getPositions() != null) {
            for(PostRequestDto.PostPositionDto positionDto : dto.getPositions()) {
                PostPosition position = new PostPosition();
                position.setPost(post);
                position.setRole(positionDto.getRole());
                position.setCount(positionDto.getCount());
                position.setStatus(positionDto.getStatus());
                post.getPositions().add(position);
            }
        }
        if(dto.getTechStacks() != null) {
            for(String techStack : dto.getTechStacks()) {
                PostStack postStack = new PostStack();
                postStack.setPost(post);
                postStack.setTechStack(techStack);
                post.getTechStacks().add(postStack);
            }
        }
        Post savedPost = postRepository.save(post);
        return savedPost.getId();
    }

    // 게시글 상세보기
    @Transactional
    public PostResponseDto selectViewByIdWithUsername(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        post.setViewCount(post.getViewCount() + 1);
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
        boolean likedByUser = postLikeRepository.findByPostIdAndMemberId(postId, member.getId()).isPresent();
        return new PostResponseDto(post, likedByUser);
    }

    // 게시글 상세보기 로그인 x
    @Transactional
    public PostResponseDto selectViewById(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        post.setViewCount(post.getViewCount() + 1);
        return new PostResponseDto(post);
    }

    // 본인이 작성한 글만 반환, 아니면 403
    public Post findOwnedPost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("게시글을 찾을 수 없습니다."));
        if (!post.getAuthor().equals(username)) {
            throw new AccessDeniedException("본인이 작성한 글만 처리할 수 있습니다.");
        }
        return post;
    }

    // 게시글 수정
    @Transactional
    public PostResponseDto updatePost(@Valid PostRequestDto dto, String username) {
        Post post = findOwnedPost(dto.getId(), username);

        if (dto.getContent() != null) {
            post.setContent(dto.getContent());
        }
        if (dto.getTitle() != null) {
            post.setTitle(dto.getTitle());
        }
        if (dto.getProjectType() != null) {
            post.setProjectType(dto.getProjectType());
        }
        if (dto.getRegion() != null) {
            post.setRegion(dto.getRegion());
        }
        if (dto.getStartDate() != null) {
            post.setStartDate(dto.getStartDate());
        }
        if (dto.getEndDate() != null) {
            post.setEndDate(dto.getEndDate());
        }
        post.setUpdated(new Date());
        // 기술 스택 업데이트
        if (dto.getTechStacks() != null) {
            post.getTechStacks().clear(); // 기존 데이터 삭제
            for (String techStack : dto.getTechStacks()) {
                PostStack postStack = new PostStack();
                postStack.setPost(post);
                postStack.setTechStack(techStack);
                post.getTechStacks().add(postStack); // 리스트에 추가
            }
        }

        // 모집 포지션은 수정하지 않는다. 포지션에 신청 내역(PostPositionMember)이 cascade로 묶여 있어
        // 지우고 다시 만들면 신청 기록이 삭제되고 인원(count)도 0이 된다. 수정 화면에도 포지션 편집 UI가 없다.

        return new PostResponseDto(post);
    }

    // 게시글 삭제
    public void deletePost(Long postId, String username) {
        postRepository.delete(findOwnedPost(postId, username));
    }

    // 게시글 전체 리스트 조회. DTO 변환 중 지연 로딩이 일어나므로 OSIV에 기대지 않고 트랜잭션 안에서 끝낸다.
    //
    // 전체 건수는 세지 않는다. 프론트는 건수를 보여 주지 않고 페이지 버튼만 그리므로 "뒤에 몇 페이지가 더 있는지"면 충분하다.
    // count는 조건에 맞는 글을 전부 읽어야 해서, 목록은 몇 ms인데 count 때문에 1~3초가 걸렸다.
    // 대신 현재 페이지부터 PAGES_AHEAD 페이지 분량 + 1건의 id만 정렬 인덱스로 읽고, 앞쪽 한 페이지만 엔티티로 가져온다.
    // 그래서 응답의 totalElements는 정확한 전체 건수가 아니라 "적어도 이만큼은 있다"는 값이다.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<PostResponseDto> getPostList(PostRequestDto dto) {
        Pageable pageable = PageRequest.of(dto.getPage(), dto.getSize(), PostSearchSpec.sort(dto.getSortType()));
        List<Long> ids = findPostIds(PostSearchSpec.of(dto), pageable, dto.getSize() * PAGES_AHEAD + 1);
        List<Long> pageIds = ids.subList(0, Math.min(dto.getSize(), ids.size()));

        Map<Long, Post> posts = postRepository.findAllById(pageIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<PostResponseDto> content = pageIds.stream()
                .map(posts::get).filter(Objects::nonNull) // 두 조회 사이에 지워진 글
                .map(PostResponseDto::new).toList();
        return new PageImpl<>(content, pageable, pageable.getOffset() + ids.size());
    }

    private List<Long> findPostIds(Specification<Post> spec, Pageable pageable, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Post> root = query.from(Post.class);
        query.select(root.get("id"))
                .where(spec.toPredicate(root, query, cb))
                .orderBy(QueryUtils.toOrders(pageable.getSort(), root, cb));
        return entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(limit)
                .getResultList();
    }

    public List<PostResponseDto> findMyPosts(String username) {
        List<Post> post = postRepository.findByAuthorOrderByUpdatedOrCreatedDesc(username);
        if(post.isEmpty()) {
            return new ArrayList<>();
        }
        return post.stream().map(PostResponseDto::new).collect(Collectors.toList());
    }


    public ResponseEntity<String> likePost(Long postId, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(()-> new RuntimeException("게시글을 찾을 수 없습니다."));
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("사용자를 찾을 수 없습니다."));
        boolean alreadyLiked = post.getLikes().stream()
                .anyMatch(like -> like.getMember().getId().equals(member.getId()));
        if (alreadyLiked) {
            return ResponseEntity.badRequest().body("이미 좋아요한 게시글입니다.");
        }

        PostLike postLike = new PostLike();
        postLike.setPost(post);
        postLike.setMember(member);
        post.getLikes().add(postLike);
        post.setLikeCount(post.getLikeCount() + 1);
        postLikeRepository.save(postLike);

        return ResponseEntity.ok("좋아요 완료");
    }


    public List<PostResponseDto> findLikePost(String username) {
        List<PostResponseDto> like = postLikeRepository.findAllByUsername(username);
        return like;
    }

    @Transactional
    public ResponseEntity<String> unLikePost(Long id, String username) {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("사용자를 찾을 수 없습니다."));
        Post post = postRepository.findById(id)
                        .orElseThrow(()-> new RuntimeException("게시글을 찾을 수 없습니다."));
        post.setLikeCount(post.getLikeCount() - 1);
        postLikeRepository.deleteByPostIdAndMemberId(id, member.getId());

        return ResponseEntity.ok("좋아요 취소 완료");
    }


    public List<ApplyMemeberDto> getPostApplyMember(Long postId) {
       List<ApplyMemeberDto> list = postPositionMemberRepository.findPostPositionMemberByPostId(postId);
        return list;
    }

    public List<MonthlyStatsDto> getMonthlyStats(int year) {
        List<Object[]> result = postRepository.countPostPerMonth(year);
        return result.stream()
                .map(obj ->
                        new MonthlyStatsDto(
                                String.format("%d월", ((Number)obj[0]).intValue()), // name
                                ((Number)obj[1]).intValue() // count
                        )
                )
                .toList();
    }

    public List<MonthlyStatsDto> getPostStauts(int year) {
        List<Object[]> result = postRepository.countPostGroupByStatus(year);
        return result.stream()
                .map(obj ->
                        new MonthlyStatsDto(
                                ((String) obj[0]), // name
                                ((Number)obj[1]).intValue() // count
                        )
                )
                .toList();
    }

    public List<MonthlyPostDto> getTotalPositionStats(int year) {
        List<Object[]> result2 = postPositionMemberRepository.countApplyMonth(year);
        List<Object[]> result3 = postPositionMemberRepository.countAcceptMonth(year);
        List<Object[]> result4 = postPositionMemberRepository.countRejectMonth(year);
        Map<Integer, MonthlyPostDto> monthMap = new HashMap<>();

        for (Object[] row : result2) {
            int month = ((Number) row[0]).intValue();
            int apply = ((Number) row[1]).intValue();
            monthMap.computeIfAbsent(month, m -> new MonthlyPostDto(m, String.format("%d월", m), 0, 0, 0))
                    .setApplyCount(apply);
        }
        for (Object[] row : result3) {
            int month = ((Number) row[0]).intValue();
            int accept = ((Number) row[1]).intValue();
            monthMap.computeIfAbsent(month, m -> new MonthlyPostDto(m, String.format("%d월", m), 0, 0, 0))
                    .setAcceptCount(accept);
        }
        for (Object[] row : result4) {
            int month = ((Number) row[0]).intValue();
            int accept = ((Number) row[1]).intValue();
            monthMap.computeIfAbsent(month, m -> new MonthlyPostDto(m, String.format("%d월", m), 0, 0, 0))
                    .setRejectCount(accept);
        }

        List<MonthlyPostDto> mergedList = new ArrayList<>(monthMap.values());
        mergedList.sort(Comparator.comparingInt(MonthlyPostDto::getMonth));
        return mergedList;
    }

    public List<PostChartResponse> getPostCategory(int year) {
        List<Object[]> result = postRepository.countPostGroupByCategory(year);
        return result.stream()
                .map(obj ->
                        new PostChartResponse(
                                String.format("%d월", ((Number)obj[0]).intValue()),
                                ((String) obj[1]), // name
                                ((Number)obj[2]).intValue() // count
                        )
                )
                .toList();
    }

    public String updateDelYn(PostRequestDto dto) {
        Post post = postRepository.findById(dto.getId())
                .orElseThrow(()-> new RuntimeException("해당 게시글을 찾을 수 없습니다."));
        post.setDelYn(dto.getDelYn());
        postRepository.save(post);

        String msg = "";
        if(dto.getDelYn().equals("Y")){
            msg = "게시글이 비공개 되었습니다.";
        } else {
            msg = "게시글이 공개 되었습니다.";
        }
        return msg;
    }
}
