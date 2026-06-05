package com.dontdelay.controller;

import com.dontdelay.domain.User;
import com.dontdelay.dto.LoginRequest;
import com.dontdelay.dto.LoginResponse;
import com.dontdelay.dto.SignupRequest;
import com.dontdelay.dto.UserProfileResponse;
import com.dontdelay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "이미 존재하는 사용자명입니다."));
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "이미 등록된 이메일입니다."));
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .realName(request.getRealName())
                .email(request.getEmail())
                .department(request.getDepartment())
                .build();

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "회원가입 성공"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return userRepository.findByUsername(request.getUsername())
                .<ResponseEntity<?>>map(user ->
                        ResponseEntity.ok(LoginResponse.from(UserProfileResponse.from(user))))
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "사용자를 찾을 수 없습니다.")));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "UNAUTHORIZED", "message", "로그인이 필요합니다."));
        }
        return userRepository.findByUsername(authentication.getName())
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(UserProfileResponse.from(user)))
                .orElseGet(() -> ResponseEntity.status(401)
                        .body(Map.of("error", "UNAUTHORIZED", "message", "사용자를 찾을 수 없습니다.")));
    }
}
