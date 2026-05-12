package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import org.springframework.web.multipart.MultipartFile;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FileService fileService;

    @Transactional
    public SignupResponse signup(SignupRequest request, MultipartFile profileImage) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        String profileImageKey = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            profileImageKey = fileService.saveProfileImage(request.getLoginId(), profileImage);
        }

        User user = userRepository.save(User.create(
                request.getLoginId(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname(),
                profileImageKey
        ));

        return SignupResponse.from(user).withImageUrl(fileService.resolveImageUrl(user.getProfileImageUrl()));
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return new LoginResponse(jwtUtil.generateToken(user.getLoginId()));
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getLoginId())
                .password(user.getPassword())
                .roles("USER")
                .build();
    }
}
