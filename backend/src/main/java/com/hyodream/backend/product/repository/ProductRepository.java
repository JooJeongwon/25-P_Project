package com.hyodream.backend.product.repository;

import com.hyodream.backend.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // 나중에 "당뇨" 태그 가진 상품 찾을 때 씀
    // (JPA가 알아서 만들어줌)
    List<Product> findByHealthBenefitsContaining(String benefit);

    // 상품명 검색(페이징)
    Page<Product> findByNameContaining(String keyword, Pageable pageable);
}