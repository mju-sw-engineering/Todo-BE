package com.todo.domain.auth.service;

import com.todo.domain.auth.dto.request.LoginRequest;
import com.todo.domain.auth.dto.request.SignupRequest;
import com.todo.domain.auth.dto.response.LoginResponse;
import com.todo.domain.auth.dto.response.SignupResponse;
import com.todo.domain.auth.entity.ConsentType;
import com.todo.domain.auth.entity.UserConsent;
import com.todo.domain.auth.repository.UserConsentRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.jwt.JwtUtil;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    private static final String TERMS_VERSION = "v1.0";

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FileService fileService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException("비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException("이미 사용 중인 아이디입니다.", HttpStatus.CONFLICT);
        }

        emailVerificationService.validateAndConsume(request.emailVerificationToken(), request.email());

        User created = User.create(
                request.loginId(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.profileImageKey()
        );
        created.assignEmail(request.email());
        User user = userRepository.save(created);

        List<UserConsent> consents = new ArrayList<>();
        consents.add(UserConsent.create(user, ConsentType.TERMS, TERMS_VERSION));
        consents.add(UserConsent.create(user, ConsentType.PRIVACY, TERMS_VERSION));
        if (Boolean.TRUE.equals(request.marketingAgreed())) {
            consents.add(UserConsent.create(user, ConsentType.MARKETING, TERMS_VERSION));
        }
        userConsentRepository.saveAll(consents);

        return SignupResponse.from(user).withImageUrl(fileService.resolveImageUrl(user.getProfileImageUrl()));
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException("아이디 또는 비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
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
