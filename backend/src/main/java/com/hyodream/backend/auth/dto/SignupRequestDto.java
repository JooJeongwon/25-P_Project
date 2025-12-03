package com.hyodream.backend.auth.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class SignupRequestDto {
    private String username; // 아이디
    private String password; // 비밀번호
    private String name; // 이름
    private String phone; // 전화번호
    private LocalDate birthDate;// 생년월일 (YYYY-MM-DD)

    // 주소 정보
    private String city;
    private String street;
    private String zipcode;
}