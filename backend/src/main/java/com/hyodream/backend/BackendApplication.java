package com.hyodream.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync // 비동기 처리를 위해 추가
@EnableScheduling // 스케줄러 사용을 위해 추가
@EnableJpaAuditing // JPA Auditing 활성화
@EnableFeignClients // Feign Client 활성화 (AiClient 사용을 위해 필수)
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
