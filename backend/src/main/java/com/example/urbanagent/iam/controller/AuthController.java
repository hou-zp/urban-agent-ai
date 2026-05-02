package com.example.urbanagent.iam.controller;

import com.example.urbanagent.common.api.ApiResponse;
import com.example.urbanagent.iam.application.JwtTokenService;
import com.example.urbanagent.iam.domain.UserContext;
import com.example.urbanagent.iam.domain.UserContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenService jwtTokenService;

    public AuthController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return jwtTokenService.authenticate(request.userId(), request.password())
                .map(token -> ApiResponse.success(new LoginResponse(token, "Bearer")))
                .orElse(ApiResponse.failure(401, "用户名或密码错误"));
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoView> me() {
        UserContext ctx = UserContextHolder.get();
        return ApiResponse.success(new UserInfoView(
                ctx.userId(),
                ctx.role(),
                ctx.region()
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(null);
    }

    public record LoginRequest(
            @NotBlank(message = "userId 不能为空") String userId,
            @NotBlank(message = "password 不能为空") String password
    ) {}

    public record LoginResponse(String accessToken, String tokenType) {}

    public record UserInfoView(String userId, String role, String region) {}
}