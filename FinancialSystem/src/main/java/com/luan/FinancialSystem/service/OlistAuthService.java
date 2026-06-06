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
    private static final String TINY_REDIRECT_URI = "http://webhook.casalamavievendas.com.br/";

    private final RestClient tokenClient;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthenticatedUserService authenticatedUserService;
    private final CryptoService cryptoService;

    public OlistAuthService(@Value("${olist.oauth.token-url}") String tokenUrl,
                            UserRepository userRepository,
                            UserService userService,
                            AuthenticatedUserService authenticatedUserService,
                            CryptoService cryptoService) {
        this.tokenClient = RestClient.builder()
                .baseUrl(tokenUrl)
                .build();
        this.userRepository = userRepository;
        this.userService = userService;
        this.authenticatedUserService = authenticatedUserService;
        this.cryptoService = cryptoService;
    }

    @Transactional
    public User saveClientCredentials(String clientId, String clientSecret) {
        System.out.println("[BACKEND OLIST SERVICE] saveClientCredentials chamado.");
        System.out.println("[BACKEND OLIST SERVICE] Client ID informado? " + (clientId != null && !clientId.isBlank()));
        System.out.println("[BACKEND OLIST SERVICE] Client Secret informado? " + (clientSecret != null && !clientSecret.isBlank()));
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
        System.out.println("------------------------------------------------------------");
        System.out.println("[BACKEND OLIST SERVICE] generateTokenFromWebhook iniciado.");
        System.out.println("[BACKEND OLIST SERVICE] Objetivo: trocar CODE por access_token e refresh_token.");
        System.out.println("[BACKEND OLIST SERVICE] Client ID recebido: " + maskClientId(clientId));
        System.out.println("[BACKEND OLIST SERVICE] Code recebido? " + (authorizationCode != null && !authorizationCode.isBlank()));
        if (authorizationCode == null || authorizationCode.isBlank()) {
            System.out.println("[BACKEND OLIST SERVICE] ERRO: code vazio ou nulo.");
            throw new IllegalArgumentException("Informe o codigo de autorizacao retornado pela Olist/Tiny.");
        }
        if (clientId == null || clientId.isBlank()) {
            System.out.println("[BACKEND OLIST SERVICE] ERRO: client_id vazio ou nulo.");
            throw new IllegalArgumentException("Informe o client_id enviado no state do callback.");
        }

        System.out.println("[BACKEND OLIST SERVICE] Etapa 1: buscando usuario pelo client_id...");
        User user = userRepository.findByClientId(clientId.trim())
                .orElseThrow(() -> new IllegalStateException("Nenhum usuario encontrado para o client_id informado."));
        System.out.println("[BACKEND OLIST SERVICE] SUCESSO: usuario encontrado.");
        System.out.println("[BACKEND OLIST SERVICE] Usuario ID: " + user.getId());
        System.out.println("[BACKEND OLIST SERVICE] Email do usuario: " + user.getEmail());
        System.out.println("[BACKEND OLIST SERVICE] Etapa 2: validando se client_id e client_secret existem...");
        ensureClientCredentials(user);
        System.out.println("[BACKEND OLIST SERVICE] SUCESSO: credenciais existem no banco.");
        logger.info("[Olist/Tiny] Usuario encontrado para code do webhook. userId={}, clientId={}",
                user.getId(),
                maskClientId(user.getClient_id()));

        System.out.println("[BACKEND OLIST SERVICE] Etapa 3: montando body x-www-form-urlencoded.");
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", authorizationCode.trim());
        body.add("redirect_uri", TINY_REDIRECT_URI);
        System.out.println("[BACKEND OLIST SERVICE] Body grant_type=authorization_code.");
        System.out.println("[BACKEND OLIST SERVICE] Body code=[oculto, recebido].");
        System.out.println("[BACKEND OLIST SERVICE] Body redirect_uri=" + TINY_REDIRECT_URI);
        System.out.println("[BACKEND OLIST SERVICE] Etapa 4: chamando endpoint do Tiny para gerar token...");

        return saveTokenResponse(user, exchangeAuthorizationCodeForTokens(user, body));
    }

    @Transactional
    public TokenStatus refreshLoggedUserToken() {
        User user = getFreshLoggedUser();
        refreshToken(user);
        return getTokenStatus(user);
    }

    @Transactional
    public String getValidAccessTokenForLoggedUser() {
        System.out.println("[BACKEND OLIST AUTH] Buscando access_token salvo no banco para usar na API Olist/Tiny...");
        User user = getFreshLoggedUser();
        ensureTokenConfigured(user);

        if (isTokenExpired(user)) {
            System.out.println("[BACKEND OLIST AUTH] Access_token expirado ou perto de expirar. Renovando com refresh_token salvo...");
            refreshToken(user);
            System.out.println("[BACKEND OLIST AUTH] Token renovado e salvo no banco.");
        }

        System.out.println("[BACKEND OLIST AUTH] Access_token encontrado no banco e descriptografado para a requisicao.");
        return cryptoService.decrypt(user.getOlist_access_token());
    }

    public TokenStatus getLoggedUserTokenStatus() {
        return getTokenStatus(getFreshLoggedUser());
    }

    private void refreshToken(User user) {
        System.out.println("[BACKEND OLIST AUTH] refreshToken iniciado usando refresh_token salvo no banco.");
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

        System.out.println("############################################################");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] AQUI ESTA A TROCA DO CODE PELO TOKEN.");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Endpoint:");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] POST https://accounts.tiny.com.br/realms/tiny/protocol/openid-connect/token");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Headers:");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Authorization: Basic base64(client_id:client_secret)");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Content-Type: application/x-www-form-urlencoded");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Body:");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] grant_type=authorization_code");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] code=[oculto]");
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] redirect_uri=" + TINY_REDIRECT_URI);
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Client ID usado: " + maskClientId(clientId));
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Client Secret existe/descriptografou? " + (clientSecret != null && !clientSecret.isBlank()));
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Basic gerado em base64? " + (basicAuthBase64 != null && !basicAuthBase64.isBlank()));
        System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Enviando agora para o Tiny...");

        try {
            OlistTokenResponse response = tokenClient.post()
                    .uri("")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthBase64)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(OlistTokenResponse.class);

            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] SUCESSO: Tiny respondeu a troca do code.");
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Veio access_token? " + (response != null && response.accessToken() != null && !response.accessToken().isBlank()));
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Veio refresh_token? " + (response != null && response.refreshToken() != null && !response.refreshToken().isBlank()));
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Veio expires_in? " + (response != null && response.expiresIn() != null));
            System.out.println("############################################################");
            return response;
        } catch (RestClientResponseException exception) {
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] ERRO: Tiny recusou a troca do code.");
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] HTTP status: " + exception.getStatusCode());
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Resposta Tiny: " + exception.getResponseBodyAsString());
            System.out.println("############################################################");
            throw exception;
        } catch (RuntimeException exception) {
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] ERRO: falha inesperada chamando Tiny.");
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Classe: " + exception.getClass().getName());
            System.out.println("[BACKEND OLIST TOKEN EXCHANGE] Mensagem: " + exception.getMessage());
            System.out.println("############################################################");
            throw exception;
        }
    }

    private OlistTokenResponse requestToken(User user, MultiValueMap<String, String> body) {
        System.out.println("[BACKEND OLIST SERVICE] requestToken iniciado.");
        System.out.println("[BACKEND OLIST SERVICE] URL Tiny: https://accounts.tiny.com.br/realms/tiny/protocol/openid-connect/token");
        System.out.println("[BACKEND OLIST SERVICE] Metodo: POST");
        System.out.println("[BACKEND OLIST SERVICE] Content-Type: application/x-www-form-urlencoded");
        System.out.println("[BACKEND OLIST SERVICE] Authorization: Basic base64(client_id:client_secret) sera gerado pelo setBasicAuth.");
        System.out.println("[BACKEND OLIST SERVICE] Client ID usado no Basic Auth: " + maskClientId(user.getClient_id()));
        System.out.println("[BACKEND OLIST SERVICE] Client Secret descriptografado? " + (userService.getDecryptedClientSecret(user) != null && !userService.getDecryptedClientSecret(user).isBlank()));
        System.out.println("[BACKEND OLIST SERVICE] Enviando requisicao para o Tiny agora...");
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
        System.out.println("[BACKEND OLIST SERVICE] saveTokenResponse iniciado.");
        System.out.println("[BACKEND OLIST SERVICE] Etapa 5: validando resposta do Tiny...");
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            System.out.println("[BACKEND OLIST SERVICE] ERRO: Tiny nao retornou access_token.");
            throw new IllegalStateException("A Olist/Tiny nao retornou access_token.");
        }

        System.out.println("[BACKEND OLIST SERVICE] SUCESSO: Tiny retornou access_token.");
        System.out.println("[BACKEND OLIST SERVICE] Tiny retornou refresh_token? " + (response.refreshToken() != null && !response.refreshToken().isBlank()));
        System.out.println("[BACKEND OLIST SERVICE] Tiny retornou expires_in: " + response.expiresIn());
        logger.info("[Olist/Tiny] Tiny retornou tokens. userId={}, hasAccessToken={}, hasRefreshToken={}, expiresIn={}",
                user.getId(),
                response.accessToken() != null && !response.accessToken().isBlank(),
                response.refreshToken() != null && !response.refreshToken().isBlank(),
                response.expiresIn());

        System.out.println("[BACKEND OLIST SERVICE] Etapa 6: criptografando access_token...");
        user.setOlist_access_token(cryptoService.encrypt(response.accessToken()));
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
            System.out.println("[BACKEND OLIST SERVICE] Etapa 7: criptografando refresh_token...");
            user.setOlist_refresh_token(cryptoService.encrypt(response.refreshToken()));
        } else {
            System.out.println("[BACKEND OLIST SERVICE] AVISO: refresh_token nao veio na resposta, mantendo valor atual.");
        }

        long expiresIn = response.expiresIn() != null ? response.expiresIn() : 0L;
        if (expiresIn > 0) {
            System.out.println("[BACKEND OLIST SERVICE] Etapa 8: calculando expiracao do token...");
            user.setOlist_token_expires_at(Instant.now().plusSeconds(expiresIn).getEpochSecond());
        } else {
            System.out.println("[BACKEND OLIST SERVICE] AVISO: expires_in nao veio ou veio zero.");
        }

        System.out.println("[BACKEND OLIST SERVICE] Etapa 9: salvando tokens criptografados no banco com saveAndFlush...");
        userRepository.saveAndFlush(user);
        System.out.println("[BACKEND OLIST SERVICE] SUCESSO: tokens salvos no banco.");
        System.out.println("[BACKEND OLIST SERVICE] Usuario ID: " + user.getId());
        System.out.println("[BACKEND OLIST SERVICE] access_token salvo? " + (user.getOlist_access_token() != null && !user.getOlist_access_token().isBlank()));
        System.out.println("[BACKEND OLIST SERVICE] refresh_token salvo? " + (user.getOlist_refresh_token() != null && !user.getOlist_refresh_token().isBlank()));
        System.out.println("[BACKEND OLIST SERVICE] token_expires_at salvo: " + user.getOlist_token_expires_at());
        logger.info("[Olist/Tiny] Tokens criptografados e salvos no usuario. userId={}, expiresAt={}",
                user.getId(),
                user.getOlist_token_expires_at());
        System.out.println("------------------------------------------------------------");
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
