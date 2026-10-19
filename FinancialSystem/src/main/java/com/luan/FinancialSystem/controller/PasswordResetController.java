package com.luan.FinancialSystem.controller;

import com.luan.FinancialSystem.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class PasswordResetController {
    private final PasswordResetService service;
    public PasswordResetController(PasswordResetService service) { this.service = service; }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgot(@Valid @RequestBody ForgotRequest request) {
        try {
            service.request(request.email());
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(Map.of("message", "Se o e-mail estiver cadastrado, você receberá um link para redefinir sua senha. Verifique também o spam."));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> reset(@Valid @RequestBody ResetRequest request) {
        try {
            service.reset(request.token(), request.password());
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(Map.of("message", "Senha alterada com sucesso. Entre com sua nova senha."));
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
        }
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalidRequest() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body(Map.of("message", "Confira os dados informados. Use um e-mail válido e uma senha de 8 a 72 caracteres."));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadableRequest() {
        return ResponseEntity.badRequest().header("Cache-Control", "no-store")
                .body(Map.of("message", "Não foi possível ler os dados enviados."));
    }

    public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetRequest(@NotBlank @Size(max = 128) String token,
                               @NotBlank @Size(min = 8, max = 72) String password) {
        @Override
        public String toString() {
            return "ResetRequest[credentials=REDACTED]";
        }
    }
}
