package com.luan.FinancialSystem.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.luan.FinancialSystem.entity.User;
import com.luan.FinancialSystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

@Service
public class OlistAuthService {
    private static final Logger logger = LoggerFactory.getLogger(OlistAuthService.class);
    private static final long TOKEN_EXPIRATION_MARGIN_SECONDS = 60L;
    private static final String TINY_REDIRECT_URI = "https://webhook.casalamavievendas.com.br/";

    private final RestClient tokenClient;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CryptoService cryptoService;
    private final com.luan.FinancialSystem.repository.OlistAuthorizationRepository authorizations;

    public OlistAuthService(@Value("${olist.oauth.token-url}") String tokenUrl,
                            UserRepository userRepository,
                            UserService userService,
                            AuthenticatedUserService authenticatedUserService,
                            CryptoService cryptoService, com.luan.FinancialSystem.repository.OlistAuthorizationRepository authorizations) {
        this.tokenClient = RestClient.builder()
                .baseUrl(tokenUrl)
                .build();
        this.userRepository = userRepository;
        this.userService = userService;
        this.authenticatedUserService = authenticatedUserService;
        this.cryptoService = cryptoService;
        this.authorizations = authorizations;
    }

    @Transactional
    public java.util.Map<String, String> authorizationUrl() {
        User logged = authenticatedUserService.getLoggedUser();
        User user = userRepository.findByEmailForPasswordReset(logged.getEmail()).orElseThrow();
        ensureClientCredentials(user);
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var state = authorizations.findById(user.getId()).orElseGet(com.luan.FinancialSystem.entity.OlistAuthorization::new);
        state.setUser(user); state.setStateHash(PasswordResetService.hash(raw));
        state.setExpiresAt(Instant.now().plusSeconds(600));
        authorizations.saveAndFlush(state);
        String url = "https://accounts.tiny.com.br/realms/tiny/protocol/openid-connect/auth"
                + "?response_type=code&client_id=" + java.net.URLEncoder.encode(user.getClient_id(), StandardCharsets.UTF_8)
                + "&redirect_uri=" + java.net.URLEncoder.encode(TINY_REDIRECT_URI, StandardCharsets.UTF_8)
                + "&state=" + raw;
        return java.util.Map.of("authorizationUrl", url);
    }

    @Transactional
    public User saveClientCredentials(String clientId, String clientSecret) {
        return userService.setClientOlist(clientId, clientSecret);
    }

    @Transactional
    public TokenStatus generateToken(String authorizationCode, String redirectUri) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IllegalArgumentException("Informe o codigo de autorizacao retornado pela Olist/Tiny.");
        }

        User user = authenticatedUserService.getLoggedUser();
        ensureClientCredentials(user);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", authorizationCode.trim());
        body.add("redirect_uri", TINY_REDIRECT_URI);

        return saveTokenResponse(user, requestToken(user, body));
    }

    @Transactional
    public TokenStatus generateTokenFromWebhook(String authorizationCode, String clientId) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IllegalArgumentException("Informe o codigo de autorizacao retornado pela Olist/Tiny.");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Informe o client_id enviado no state do callback.");
        }
        if (!clientId.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Autorização inválida ou expirada.");
        var state = authorizations.findForUpdate(PasswordResetService.hash(clientId))
                .orElseThrow(() -> new IllegalArgumentException("Autorização inválida ou expirada."));
        if (state.getExpiresAt() == null || !state.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalArgumentException("Autorização inválida ou expirada.");
        User user = state.getUser();
        ensureClientCredentials(user);
        logger.info("[Olist/Tiny] Usuario encontrado para code do webhook. userId={}, clientId={}",
                user.getId(),
                maskClientId(user.getClient_id()));
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", authorizationCode.trim());
        body.add("redirect_uri", TINY_REDIRECT_URI);

        var result = saveTokenResponse(user, exchangeAuthorizationCodeForTokens(user, body));
        authorizations.delete(state);
        return result;
    }

    @Transactional
    public TokenStatus refreshLoggedUserToken() {
        User user = getFreshLoggedUser();
        refreshToken(user);
        return getTokenStatus(user);
    }

    @Transactional
    public String getValidAccessTokenForLoggedUser() {
        User user = getFreshLoggedUser();
        ensureTokenConfigured(user);

        if (isTokenExpired(user)) {
            refreshToken(user);
        }
        return cryptoService.decrypt(user.getOlist_access_token());
    }

    @Transactional
    public String getValidAccessTokenForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuario da importacao nao encontrado."));
        ensureTokenConfigured(user);

        if (isTokenExpired(user)) {
            refreshToken(user);
        }

        return cryptoService.decrypt(user.getOlist_access_token());
    }

    @Transactional
    public void refreshUserToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Usuario da importacao nao encontrado."));
        refreshToken(user);
    }

    public TokenStatus getLoggedUserTokenStatus() {
        return getTokenStatus(getFreshLoggedUser());
    }

    private void refreshToken(User user) {
        ensureClientCredentials(user);

        if (user.getOlist_refresh_token() == null || user.getOlist_refresh_token().isBlank()) {
            throw new IllegalStateException("Gere o token da Olist/Tiny antes de tentar renovar.");
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", cryptoService.decrypt(user.getOlist_refresh_token()));

        saveTokenResponse(user, requestToken(user, body));
    }

    private User getFreshLoggedUser() {
        User authenticatedUser = authenticatedUserService.getLoggedUser();
        return userRepository.findById(authenticatedUser.getId())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado no banco."));
    }

    private OlistTokenResponse exchangeAuthorizationCodeForTokens(User user, MultiValueMap<String, String> body) {
        String clientId = user.getClient_id();
        String clientSecret = userService.getDecryptedClientSecret(user);
        String credentials = clientId + ":" + clientSecret;
        String basicAuthBase64 = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        try {
            OlistTokenResponse response = tokenClient.post()
                    .uri("")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthBase64)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(OlistTokenResponse.class);
            return response;
        } catch (RestClientResponseException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private OlistTokenResponse requestToken(User user, MultiValueMap<String, String> body) {
        logger.info("[Olist/Tiny] Executando requisicao equivalente ao curl de token. tokenUrl={}, clientId={}, grantType={}, redirectUri={}",
                "https://accounts.tiny.com.br/realms/tiny/protocol/openid-connect/token",
                maskClientId(user.getClient_id()),
                body.getFirst("grant_type"),
                body.getFirst("redirect_uri"));

        return tokenClient.post()
                .uri("")
                .headers(headers -> headers.setBasicAuth(
                        user.getClient_id(),
                        userService.getDecryptedClientSecret(user),
                        StandardCharsets.UTF_8
                ))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(OlistTokenResponse.class);
    }

    private TokenStatus saveTokenResponse(User user, OlistTokenResponse response) {
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("A Olist/Tiny nao retornou access_token.");
        }
        logger.info("[Olist/Tiny] Tiny retornou tokens. userId={}, hasAccessToken={}, hasRefreshToken={}, expiresIn={}",
                user.getId(),
                response.accessToken() != null && !response.accessToken().isBlank(),
                response.refreshToken() != null && !response.refreshToken().isBlank(),
                response.expiresIn());
        user.setOlist_access_token(cryptoService.encrypt(response.accessToken()));
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
            user.setOlist_refresh_token(cryptoService.encrypt(response.refreshToken()));
        } else {
        }

        long expiresIn = response.expiresIn() != null ? response.expiresIn() : 0L;
        if (expiresIn > 0) {
            user.setOlist_token_expires_at(Instant.now().plusSeconds(expiresIn).getEpochSecond());
        } else {
        }
        userRepository.saveAndFlush(user);
        logger.info("[Olist/Tiny] Tokens criptografados e salvos no usuario. userId={}, expiresAt={}",
                user.getId(),
                user.getOlist_token_expires_at());
        return getTokenStatus(user);
    }

    private void ensureClientCredentials(User user) {
        if (user.getClient_id() == null || user.getClient_id().isBlank()
                || user.getClient_secret() == null || user.getClient_secret().isBlank()) {
            throw new IllegalStateException("Configure o client_id e o client_secret da Olist/Tiny antes de gerar token.");
        }
    }

    private void ensureTokenConfigured(User user) {
        if (user.getOlist_access_token() == null || user.getOlist_access_token().isBlank()) {
            throw new IllegalStateException("Gere o token da Olist/Tiny antes de importar dados.");
        }
    }

    private boolean isTokenExpired(User user) {
        Long expiresAt = user.getOlist_token_expires_at();
        return expiresAt != null
                && Instant.now().plusSeconds(TOKEN_EXPIRATION_MARGIN_SECONDS).getEpochSecond() >= expiresAt;
    }

    private TokenStatus getTokenStatus(User user) {
        return new TokenStatus(
                user.getClient_id(),
                user.getClient_secret() != null && !user.getClient_secret().isBlank(),
                user.getOlist_access_token() != null && !user.getOlist_access_token().isBlank(),
                user.getOlist_refresh_token() != null && !user.getOlist_refresh_token().isBlank(),
                user.getOlist_token_expires_at()
        );
    }

    private String maskClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "";
        }
        String trimmed = clientId.trim();
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12) + "...";
    }

    private record OlistTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
    }

    public record TokenStatus(String clientId,
                              boolean hasClientSecret,
                              boolean hasAccessToken,
                              boolean hasRefreshToken,
                              Long tokenExpiresAt) {
    }
}
