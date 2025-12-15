package com.hyodream.backend.product.repository;

import com.hyodream.backend.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // 상세 정보 함께 조회 (N+1 방지)
    @EntityGraph(attributePaths = {"analysis"})
    Optional<Product> findById(Long id);

    Optional<Product> findByNaverProductId(String naverProductId);

    // 업데이트된 지 오래된 상품 조회 (배치 처리용)
    List<Product> findByUpdatedAtBefore(LocalDateTime dateTime);

    // 나중에 "당뇨" 태그 가진 상품 찾을 때 씀
    // (JPA가 알아서 만들어줌)
    List<Product> findByHealthBenefitsContaining(String benefit);

    // 상품명 검색(페이징)
    Page<Product> findByNameContaining(String keyword, Pageable pageable);

    List<Product> findTop5ByHealthBenefitsContainingOrderByIdDesc(String benefit);

    // [Real-time Rec] 효능(List) 또는 카테고리(1~4)에 키워드가 포함된 상품 검색 (알레르기 필터링 추가)
    @Query("SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.healthBenefits hb " +
           "WHERE (hb LIKE %:keyword% " +
           "OR p.category1 LIKE %:keyword% " +
           "OR p.category2 LIKE %:keyword% " +
           "OR p.category3 LIKE %:keyword% " +
           "OR p.category4 LIKE %:keyword%) " +
           "AND (:isLogin = false OR NOT EXISTS (SELECT 1 FROM p.allergens a WHERE a IN :userAllergies)) " +
           "ORDER BY p.recentSales DESC, p.id DESC")
    List<Product> findByKeywordInBenefitsOrCategoriesWithAllergyCheck(
            @Param("keyword") String keyword,
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies);

    // [Health Goal Rec] 특정 효능을 가진 상품 검색 (알레르기 필터링 추가)
    @Query("SELECT DISTINCT p FROM Product p " +
            "JOIN p.healthBenefits hb " +
            "WHERE hb LIKE %:benefit% " +
            "AND (:isLogin = false OR NOT EXISTS (SELECT 1 FROM p.allergens a WHERE a IN :userAllergies)) " +
            "ORDER BY p.recentSales DESC, p.id DESC")
    List<Product> findByHealthBenefitsContainingWithAllergyCheck(
            @Param("benefit") String benefit,
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies);

    boolean existsByName(String name);

    // 커스텀 정렬 & 필터링 쿼리
    // Filtering: 내 알레르기 리스트(:userAllergies)에 포함된 성분이 하나라도 있으면 제외
    // Sorting: 내 관심사(:interest)가 healthBenefits에 포함되면 우선순위 0 (상단), 아니면 1 (하단) ->
    // 그 뒤엔 ID 최신순
    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.analysis " +
            "WHERE (:isLogin = false OR NOT EXISTS (SELECT 1 FROM p.allergens a WHERE a IN :userAllergies))")
    Page<Product> findAllWithPersonalization(
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies,
            Pageable pageable);

    // 검색어 포함 + 알러지 필터링
    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.analysis " +
            "WHERE p.name LIKE %:keyword% " +
            "AND (:isLogin = false OR NOT EXISTS (SELECT 1 FROM p.allergens a WHERE a IN :userAllergies))")
    Page<Product> findByNameContainingWithPersonalization(
            @Param("keyword") String keyword,
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies,
            Pageable pageable);

    // 연관 상품 추천 (함께 많이 산 상품 TOP 5, 취소된 주문 제외)
    @Query(value = """
                SELECT p.* FROM products p
                JOIN order_items oi_other ON p.id = oi_other.product_id
                JOIN orders o ON oi_other.order_id = o.id
                WHERE oi_other.order_id IN (
                    SELECT oi_target.order_id
                    FROM order_items oi_target
                    WHERE oi_target.product_id = :targetProductId
                )
                AND p.id != :targetProductId
                AND o.status = 'ORDER'
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

    // [New] 특정 지병(diseaseName)을 가진 유저들이 많이 구매한 상품 TOP 3 (알레르기 필터링 추가)
    @Query(value = """
            SELECT p.* 
            FROM products p
            JOIN order_items oi ON p.id = oi.product_id
            JOIN orders o ON oi.order_id = o.id
            WHERE o.user_id IN (
                -- 해당 지병을 가진 유저들의 ID 목록
                SELECT ud.user_id 
                FROM user_diseases ud
                JOIN diseases d ON ud.disease_id = d.id
                WHERE d.name = :diseaseName
            )
            AND (
                 :isLogin = false 
                 OR NOT EXISTS (
                     SELECT 1 FROM product_allergens pa 
                     WHERE pa.product_id = p.id 
                     AND pa.allergen IN :userAllergies
                 )
            )
            GROUP BY p.id
            ORDER BY COUNT(oi.id) DESC
            LIMIT 3
            """, nativeQuery = true)
    List<Product> findTopSellingProductsByDiseaseWithAllergyCheck(
            @Param("diseaseName") String diseaseName,
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies);

    // AI 추천 후보군 (인기순 30개 - 알레르기 제외)
    @Query("SELECT p FROM Product p " +
           "WHERE (:isLogin = false OR NOT EXISTS (SELECT 1 FROM p.allergens a WHERE a IN :userAllergies)) " +
           "ORDER BY p.recentSales DESC " +
           "LIMIT 30")
    List<Product> findTop30SafeByRecentSales(
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies);

    // AI 추천 후보군 (신상품순 20개 - 알레르기 제외)
    @Query("SELECT p FROM Product p " +
           "WHERE (:isLogin = false OR NOT EXISTS (SELECT 1 FROM p.allergens a WHERE a IN :userAllergies)) " +
           "ORDER BY p.createdAt DESC " +
           "LIMIT 20")
    List<Product> findTop20SafeByCreatedAt(
            @Param("isLogin") boolean isLogin,
            @Param("userAllergies") List<String> userAllergies);

    // AI 추천 후보군 (단순 인기순 80개 - 알레르기 필터링 없음)
    List<Product> findTop80ByOrderByRecentSalesDesc();

    // AI 추천 후보군 (단순 신상품순 20개 - 알레르기 필터링 없음)
    List<Product> findTop20ByOrderByCreatedAtDesc();

    // [Concurrency Control] 비관적 락(Pessimistic Lock)을 이용한 단일 조회
    // SELECT ... FOR UPDATE 구문이 실행되어 다른 트랜잭션의 접근을 차단함 (줄 세우기)
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.analysis WHERE p.id = :id")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    // [Concurrency Fix] JPA를 거치지 않고 DB 레벨에서 원자적으로 동기화 상태 선점 (Upsert)
    // 리턴값: 1 이상이면 내가 선점(Insert or Update 성공), 0이면 이미 진행 중(선점 실패)
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        INSERT INTO review_analysis (product_id, status, positive_count, negative_count, positive_ratio, negative_ratio, analyzed_review_count, last_analyzed_at)
        VALUES (:productId, 'PROGRESS', 0, 0, 0.0, 0.0, 0, NOW())
        ON DUPLICATE KEY UPDATE
            status = IF(status = 'PROGRESS', status, 'PROGRESS')
    """, nativeQuery = true)
    int startSyncNative(@Param("productId") Long productId);

}
