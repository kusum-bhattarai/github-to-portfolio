package com.portfolio.backend.security;

import com.portfolio.backend.entity.GitHubConnection;
import com.portfolio.backend.entity.User;
import com.portfolio.backend.repository.GitHubConnectionRepository;
import com.portfolio.backend.repository.UserRepository;
import com.portfolio.backend.service.TokenEncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final GitHubConnectionRepository connectionRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        Long githubId = ((Number) oauthUser.getAttribute("id")).longValue();
        String username = oauthUser.getAttribute("login");
        String email = oauthUser.getAttribute("email");
        String avatarUrl = oauthUser.getAttribute("avatar_url");

        // Upsert user
        User user = userRepository.findByGithubId(githubId)
                .orElseGet(() -> {
                    log.info("New user signing in: {}", username);
                    return User.builder()
                            .githubId(githubId)
                            .username(username)
                            .email(email)
                            .avatarUrl(avatarUrl)
                            .build();
                });

        user.setUsername(username);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        // Encrypt and store access token
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            String rawToken = authorizedClient.getAccessToken().getTokenValue();
            String encrypted = tokenEncryptionService.encrypt(rawToken);

            GitHubConnection connection = connectionRepository.findByUser(user)
                    .orElseGet(() -> GitHubConnection.builder().user(user).build());

            connection.setEncryptedAccessToken(encrypted);
            connection.setConnectedAt(OffsetDateTime.now());
            connectionRepository.save(connection);
        }

        log.info("User authenticated: {}", username);
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/dashboard");
    }
}
