package com.hyodream.backend.global.config;

import com.hyodream.backend.user.domain.Allergy;
import com.hyodream.backend.user.domain.Disease;
import com.hyodream.backend.user.domain.HealthGoal;
import com.hyodream.backend.user.repository.AllergyRepository;
import com.hyodream.backend.user.repository.DiseaseRepository;
import com.hyodream.backend.user.repository.HealthGoalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInit implements CommandLineRunner {

    private final DiseaseRepository diseaseRepository;
    private final AllergyRepository allergyRepository; // 추가
    private final HealthGoalRepository healthGoalRepository; // 추가

    @Override
    public void run(String... args) throws Exception {
        // 1. 지병
        String[] diseases = { "당뇨", "고혈압", "신장질환", "고지혈증", "골다공증", "백내장", "관절염" };
        for (String name : diseases) {
            // ⭐ 핵심: "DB에 이 이름이 없으면" 저장해라!
            if (diseaseRepository.findByName(name).isEmpty()) {
                Disease d = new Disease();
                d.setName(name);
                diseaseRepository.save(d);
            }
        }
        // 2. 알레르기
        String[] allergies = { "견과류", "우유", "달걀", "대두", "밀", "생선", "갑각류" };
        for (String name : allergies) {
            if (allergyRepository.findByName(name).isEmpty()) {
                Allergy a = new Allergy();
                a.setName(name);
                allergyRepository.save(a);
            }
        }

        // 3. 기대효과
        String[] goals = { "면역력 강화", "피로 회복", "관절/뼈 건강", "눈 건강", "기억력 개선", "혈행 개선", "장 건강" };
        for (String name : goals) {
            if (healthGoalRepository.findByName(name).isEmpty()) {
                HealthGoal h = new HealthGoal();
                h.setName(name);
                healthGoalRepository.save(h);
            }
        }

        System.out.println("✅ 마스터 데이터 동기화 완료 (없는 항목만 추가)");
    }
}