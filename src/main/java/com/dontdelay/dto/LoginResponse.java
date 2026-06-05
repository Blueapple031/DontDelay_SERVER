package com.dontdelay.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private final String message;
    private final String username;
    private final String realName;
    private final String email;
    private final String department;
    private final String major;

    public static LoginResponse from(UserProfileResponse profile) {
        return LoginResponse.builder()
                .message("로그인 성공")
                .username(profile.getUsername())
                .realName(profile.getRealName())
                .email(profile.getEmail())
                .department(profile.getDepartment())
                .major(profile.getMajor())
                .build();
    }
}
