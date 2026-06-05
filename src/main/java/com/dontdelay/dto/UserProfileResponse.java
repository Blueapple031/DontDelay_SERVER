package com.dontdelay.dto;

import com.dontdelay.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {

    private final String username;
    private final String realName;
    private final String email;
    private final String department;
    private final String major;

    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .username(user.getUsername())
                .realName(user.getRealName())
                .email(user.getEmail())
                .department(user.getDepartment())
                .major(user.getDepartment())
                .build();
    }
}
