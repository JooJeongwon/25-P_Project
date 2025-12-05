package com.hyodream.backend.product.repository;

import com.hyodream.backend.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // 나중에 "당뇨" 태그 가진 상품 찾을 때 씀
    // (JPA가 알아서 만들어줌)
    List<Product> findByHealthBenefitsContaining(String benefit);

    // 상품명 검색(페이징)
    Page<Product> findByNameContaining(String keyword, Pageable pageable);

    List<Product> findTop5ByHealthBenefitsContainingOrderByIdDesc(String benefit);

    // 커스텀 정렬 & 필터링 쿼리
    // Filtering: 내 알레르기 리스트(:userAllergies)에 포함된 성분이 하나라도 있으면 제외
    // Sorting: 내 관심사(:interest)가 healthBenefits에 포함되면 우선순위 0 (상단), 아니면 1 (하단) ->
    // 그 뒤엔 ID 최신순
    @Query("SELECT DISTINCT p FROM Product p " +
            "WHERE (:isLogin = false OR NOT EXISTS (SELECT 1 FROM p.allergens a WHERE a IN :userAllergies))")
    Page<Product> findAllWithPersonalization(
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies,
            Pageable pageable);

    // 연관 상품 추천 (함께 많이 산 상품 TOP 5)
    @Query(value = """
                SELECT p.* FROM products p
                JOIN order_items oi_other ON p.id = oi_other.product_id
                WHERE oi_other.order_id IN (
                    SELECT oi_target.order_id
                    FROM order_items oi_target
                    WHERE oi_target.product_id = :targetProductId
                )
                AND p.id != :targetProductId
                GROUP BY p.id
                ORDER BY COUNT(p.id) DESC
                LIMIT 5
            """, nativeQuery = true)
    List<Product> findFrequentlyBoughtTogether(@Param("targetProductId") Long targetProductId);

    // 태그(효능)가 많이 겹치는 순서대로 추천 (Fallback용)
    @Query(value = """
                SELECT p.* FROM products p
                JOIN product_benefits pb ON p.id = pb.product_id
                WHERE pb.benefit IN (
                    -- 현재 상품(targetId)이 가진 태그들을 서브쿼리로 가져옴
                    SELECT pb_target.benefit
                    FROM product_benefits pb_target
                    WHERE pb_target.product_id = :targetId
                )
                AND p.id != :targetId -- 자기 자신 제외
                GROUP BY p.id
                ORDER BY COUNT(pb.benefit) DESC, p.id DESC -- 겹치는 개수 많은 순 -> 최신순
                LIMIT 5
            """, nativeQuery = true)
    List<Product> findSimilarProductsByBenefits(@Param("targetId") Long targetId);

}
