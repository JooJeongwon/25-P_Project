package com.hyodream.backend.global.config;

import com.hyodream.backend.order.dto.OrderRequestDto;
import com.hyodream.backend.order.repository.OrderItemRepository;
import com.hyodream.backend.order.service.OrderService;
import com.hyodream.backend.product.domain.Product;
import com.hyodream.backend.product.dto.ReviewRequestDto;
import com.hyodream.backend.product.naver.service.NaverShoppingService;
import com.hyodream.backend.product.repository.ProductRepository;
import com.hyodream.backend.product.domain.SearchLog;
import com.hyodream.backend.product.repository.SearchLogRepository;
import com.hyodream.backend.product.repository.ReviewRepository;
import com.hyodream.backend.product.service.ReviewService;
import com.hyodream.backend.user.domain.*;
import com.hyodream.backend.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DbSeeder {

    private final DiseaseRepository diseaseRepository;
    private final AllergyRepository allergyRepository;
    private final HealthGoalRepository healthGoalRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final SearchLogRepository searchLogRepository;

    private final PasswordEncoder passwordEncoder;
    private final NaverShoppingService naverShoppingService;
    private final OrderService orderService;
    private final ReviewService reviewService;

    @Transactional
    public void seedAll() {
        seedMetadata();
        seedUsers();
        seedProducts();
        seedOrders();
        seedReviews();
        log.info("🎉 모든 데이터 시딩이 완료되었습니다.");
    }

    private void seedMetadata() {
        String[] diseases = { "당뇨", "고혈압", "신장질환", "고지혈증", "골다공증", "백내장", "관절염" };
        for (String name : diseases) {
            if (diseaseRepository.findByName(name).isEmpty()) {
                Disease d = new Disease();
                d.setName(name);
                diseaseRepository.save(d);
            }
        }

        String[] allergies = {
                "난류(달걀)", "우유", "메밀", "밀", "대두", "땅콩", "호두", "잣",
                "고등어", "게", "새우", "오징어", "조개류", "돼지고기", "쇠고기", "닭고기",
                "복숭아", "토마토", "아황산류"
        };
        for (String name : allergies) {
            if (allergyRepository.findByName(name).isEmpty()) {
                Allergy a = new Allergy();
                a.setName(name);
                allergyRepository.save(a);
            }
        }

        String[] goals = { "면역력 강화", "피로 회복", "관절/뼈 건강", "눈 건강", "기억력 개선", "혈행 개선", "장 건강" };
        for (String name : goals) {
            if (healthGoalRepository.findByName(name).isEmpty()) {
                HealthGoal h = new HealthGoal();
                h.setName(name);
                healthGoalRepository.save(h);
            }
        }
        log.info("✅ 메타데이터 시딩 완료");
    }

    private void seedUsers() {
        if (userRepository.count() > 100) {
            if (userRepository.count() > 400) {
                log.info("ℹ️ 유저 데이터가 이미 충분하여(>400) 스킵합니다.");
                return;
            }
        }

        log.info("🚀 유저 데이터 시딩 시작 (목표: 500명)...");
        List<Disease> allDiseases = diseaseRepository.findAll();
        List<Allergy> allAllergies = allergyRepository.findAll();
        List<HealthGoal> allGoals = healthGoalRepository.findAll();
        Random random = new Random();
        String password = passwordEncoder.encode("1234");

        // 1. Admin
        if (userRepository.findByUsername("admin").isEmpty()) {
            createUser(1, "admin", "관리자", password, null, null, null);
        }

        // 2. Users (ID 2 ~ 500)
        int targetId = 500;

        for (int i = 2; i <= targetId; i++) {
            String username = "user" + i;
            if (userRepository.findByUsername(username).isPresent())
                continue;

            String name = "사용자" + i;
            List<Disease> userDiseases = new ArrayList<>();
            List<Allergy> userAllergies = new ArrayList<>();
            List<HealthGoal> userGoals = new ArrayList<>();

            // 1~15 (15명): Clean (건강한 사용자)
            if (i > 16) {
                // 나머지 (485명): 랜덤하게 여러 질병, 알러지, 건강목표 보유
                // 질병: 0~3개
                userDiseases = getRandomSubList(allDiseases, random, 3);
                // 알러지: 0~3개
                userAllergies = getRandomSubList(allAllergies, random, 3);
                // 건강목표: 1~3개 (최소 1개는 있도록)
                userGoals = getRandomSubList(allGoals, random, 3);
                if (userGoals.isEmpty()) {
                    userGoals.add(getRandomItem(allGoals, random));
                }
            }

            createUser(i, username, name, password, userDiseases, userAllergies, userGoals);
        }
        log.info("✅ 유저 데이터 시딩 완료");
    }

    private void seedProducts() {
        if (productRepository.count() > 500) {
            log.info("ℹ️ 상품 데이터가 충분하여(>500) 스킵합니다.");
            return;
        }
        log.info("🚀 상품 데이터 시딩 시작 (Naver API)...");

        String[] keywords = {
                // 1. 건강기능식품 (15개) - 효능 매핑 위주
                "홍삼", "비타민C", "프로폴리스", "밀크씨슬", "비타민B",
                "칼슘", "MSM", "루테인", "오메가3", "징코",
                "코엔자임Q10", "유산균", "알로에", "감마리놀렌산", "마그네슘",

                // 2. 식품 (15개) - 알러지 매핑 위주
                "계란", "우유", "치즈", "메밀국수", "두유",
                "땅콩", "호두", "고등어", "간장게장", "새우",
                "오징어", "전복죽", "돼지고기", "소고기", "닭가슴살",

                // 3. 의료/보조기구 (5개)
                "안마기", "찜질기", "혈압계", "지팡이", "보청기"
        };

        Random random = new Random();

        for (String keyword : keywords) {
            try {
                // 시딩 시에는 모든 상품을 가져와야 하므로 SecurityContext를 비워둠
                SecurityContextHolder.clearContext();
                naverShoppingService.importNaverProducts(keyword);

                // SearchLog 저장
                SearchLog searchLog = searchLogRepository.findById(keyword)
                        .orElse(new SearchLog(keyword, null, null));

                // 최근 2일 이내 랜덤한 시간에 검색된 것으로 설정
                searchLog.setLastSearchedAt(LocalDateTime.now().minusHours(random.nextInt(24 * 2)));
                searchLog.setLastApiCallAt(LocalDateTime.now());
                searchLogRepository.save(searchLog);

                Thread.sleep(200);
            } catch (Exception e) {
                log.error("❌ 상품 가져오기 실패 (키워드: {}): {}", keyword, e.getMessage());
            }
        }
        log.info("✅ 상품 데이터 시딩 완료");
    }

    private void seedOrders() {
        if (orderItemRepository.count() > 1000) {
            log.info("ℹ️ 주문 데이터가 충분하여(>1000) 스킵합니다.");
            return;
        }
        log.info("🚀 주문 데이터 생성 시작 (총 2000건)...");

        List<User> users = userRepository.findAll();
        List<Product> products = productRepository.findAll();
        Random random = new Random();

        // Admin 제외
        users = users.stream().filter(u -> !u.getUsername().equals("admin")).toList();

        if (products.isEmpty()) {
            log.warn("⚠️ 상품이 없어서 주문을 생성할 수 없습니다.");
            return;
        }

        List<Product> popularProducts = products.subList(0, Math.min(30, products.size()));

        // 총 2000건의 주문 생성
        for (int i = 0; i < 2000; i++) {
            User user = users.get(random.nextInt(users.size()));
            setSecurityContext(user);

            int itemCount = 1 + random.nextInt(3);
            List<OrderRequestDto> items = new ArrayList<>();

            for (int j = 0; j < itemCount; j++) {
                Product p;
                if (random.nextBoolean()) {
                    p = popularProducts.get(random.nextInt(popularProducts.size()));
                } else {
                    p = products.get(random.nextInt(products.size()));
                }

                OrderRequestDto dto = new OrderRequestDto();
                dto.setProductId(p.getId());
                dto.setCount(1 + random.nextInt(2));
                items.add(dto);
            }

            try {
                orderService.order(items);
            } catch (Exception e) {
                log.error("❌ 주문 생성 실패 (User: {}): {}", user.getUsername(), e.getMessage());
            }
        }
        log.info("✅ 주문 데이터 시딩 완료");
    }

    private void seedReviews() {
        if (reviewRepository.count() > 500) {
            log.info("ℹ️ 리뷰 데이터가 충분하여(>500) 스킵합니다.");
            return;
        }
        // ... (이하 동일)
        log.info("🚀 리뷰 데이터 생성 시작...");

        List<User> users = userRepository.findAll();
        users = users.stream().filter(u -> !u.getUsername().equals("admin")).toList();
        Random random = new Random();

        // 리뷰 텍스트 다양화 (종류 2배 증가)
        String[] goodComments = {
                "배송이 빠르고 좋습니다.", "효과가 있는 것 같아요.", "재구매 의사 있습니다.",
                "포장이 꼼꼼해요.", "가격 대비 훌륭합니다.", "선물용으로 딱입니다.",
                "유통기한이 넉넉해서 좋아요.", "생각보다 괜찮아요.", "맛이 거부감이 없네요.",
                "사용하기 정말 편해요.", "디자인이 고급스러워요.", "설명서가 잘 되어 있어요.",
                "튼튼해서 오래 쓸 것 같아요.", "기대 이상입니다.", "주변에 추천하고 싶어요."
        };
        String[] badComments = {
                "생각보다 별로네요.", "배송이 조금 늦었어요.", "포장이 뜯겨서 왔네요.",
                "효과를 잘 모르겠어요.", "가격이 좀 비싼 감이 있네요.", "사진이랑 좀 달라요.",
                "사용법이 너무 어려워요.", "마감이 좀 거치네요.", "냄새가 좀 납니다.",
                "생각보다 무거워요.", "광고랑 차이가 있네요."
        };

        for (User user : users) {
            setSecurityContext(user);
            List<Long> boughtProductIds = orderItemRepository.findProductIdsByUserId(user.getId());

            for (Long pId : boughtProductIds) {
                if (random.nextDouble() > 0.5) { // 50% 확률로 리뷰 작성
                    try {
                        String content;
                        int score;

                        // 80% 긍정, 20% 부정
                        if (random.nextDouble() < 0.8) {
                            content = goodComments[random.nextInt(goodComments.length)];
                            score = 4 + random.nextInt(2); // 4~5점
                        } else {
                            content = badComments[random.nextInt(badComments.length)];
                            score = 1 + random.nextInt(3); // 1~3점
                        }

                        ReviewRequestDto dto = new ReviewRequestDto();
                        dto.setProductId(pId);
                        dto.setContent(content);
                        dto.setScore(score);

                        reviewService.createReview(dto);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        log.info("✅ 리뷰 데이터 시딩 완료");
    }

    // --- Helpers ---

    private void setSecurityContext(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles("USER")
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private <T> T getRandomItem(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }

    // 랜덤하게 0~maxCount 개의 아이템을 선택하여 반환 (중복 제거)
    private <T> List<T> getRandomSubList(List<T> list, Random random, int maxCount) {
        if (list == null || list.isEmpty())
            return new ArrayList<>();

        int count = random.nextInt(maxCount + 1); // 0 ~ maxCount
        if (count == 0)
            return new ArrayList<>();

        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, random);

        return copy.subList(0, Math.min(count, copy.size()));
    }

    private void createUser(int id, String username, String name, String password,
            List<Disease> diseases, List<Allergy> allergies, List<HealthGoal> goals) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setName(name);
        user.setPhone("010-0000-" + String.format("%04d", id));
        int age = 60 + new Random().nextInt(31);
        user.setBirthDate(LocalDate.now().minusYears(age));
        Address address = new Address("서울시 강남구", "테헤란로 " + id + "길", "12345");
        user.setAddress(address);

        if (diseases != null) {
            for (Disease d : diseases)
                user.addDisease(UserDisease.createUserDisease(d));
        }
        if (allergies != null) {
            for (Allergy a : allergies)
                user.addAllergy(UserAllergy.createUserAllergy(a));
        }
        if (goals != null) {
            for (HealthGoal h : goals)
                user.addHealthGoal(UserHealthGoal.createUserHealthGoal(h));
        }
        userRepository.save(user);
    }
}