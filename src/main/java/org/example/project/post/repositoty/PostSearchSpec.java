package org.example.project.post.repositoty;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.example.project.post.dto.PostRequestDto;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostStack;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

// 모집글 목록 검색 조건
//
// 들어온 조건만 WHERE에 넣는다. 예전처럼 모든 조건을 (:x IS NULL OR ...)로 한 쿼리에 넣으면
// 필터가 없어도 포지션·기술스택을 조인해 글이 중복되고, 이를 DISTINCT로 되돌리느라 100만 건 기준 수십 초가 걸렸다.
// 포지션·기술스택 조건은 조인 대신 EXISTS로 걸러 글이 한 번만 나오게 한다.
public final class PostSearchSpec {

    private static final char ESCAPE = '\\';

    private PostSearchSpec() {
    }

    public static Specification<Post> of(PostRequestDto dto) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            equal(where, cb, root.get("category"), dto.getCategory());
            equal(where, cb, root.get("status"), dto.getStatus());
            equal(where, cb, root.get("postStatus"), dto.getPostStatus());
            equal(where, cb, root.get("projectType"), dto.getProjectType());
            equal(where, cb, root.get("region"), dto.getRegion());

            if (StringUtils.hasText(dto.getPosition())) {
                Subquery<Integer> position = query.subquery(Integer.class);
                Root<PostPosition> pp = position.from(PostPosition.class);
                position.select(cb.literal(1))
                        .where(cb.equal(pp.get("post"), root), cb.equal(pp.get("role"), dto.getPosition()));
                where.add(cb.exists(position));
            }
            if (StringUtils.hasText(dto.getStackKeyword())) {
                Subquery<Integer> stack = query.subquery(Integer.class);
                Root<PostStack> ps = stack.from(PostStack.class);
                stack.select(cb.literal(1))
                        .where(cb.equal(ps.get("post"), root), cb.like(ps.get("techStack"), contains(dto.getStackKeyword()), ESCAPE));
                where.add(cb.exists(stack));
            }
            if (StringUtils.hasText(dto.getKeyword())) {
                String pattern = contains(dto.getKeyword());
                where.add(cb.or(
                        cb.like(root.get("title"), pattern, ESCAPE),
                        cb.like(root.get("content"), pattern, ESCAPE),
                        cb.like(root.get("author"), pattern, ESCAPE)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    // 두 정렬 모두 (정렬 컬럼, id) 인덱스를 거꾸로 읽는다. id는 같은 값끼리 순서가 흔들리지 않게 붙인다.
    public static Sort sort(String sortType) {
        String column = "popular".equalsIgnoreCase(sortType) ? "likeCount" : "lastModified";
        return Sort.by(Sort.Order.desc(column), Sort.Order.desc("id"));
    }

    // 프론트는 빈 필터를 ""로 보낸다
    private static void equal(List<Predicate> where, CriteriaBuilder cb, Expression<String> column, String value) {
        if (StringUtils.hasText(value)) {
            where.add(cb.equal(column, value));
        }
    }

    // 사용자가 입력한 %, _ 는 와일드카드가 아니라 글자로 검색한다
    private static String contains(String keyword) {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
