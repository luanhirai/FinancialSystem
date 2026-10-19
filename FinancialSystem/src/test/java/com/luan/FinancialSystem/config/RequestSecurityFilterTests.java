package com.luan.FinancialSystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class RequestSecurityFilterTests {
    @Test void rateLimitCannotBeBypassedWithForwardedHeaders() throws Exception {
        var filter = new RequestSecurityFilter("https://app.example", "secret", false);
        var calls = new AtomicInteger();
        for (int i = 0; i < 21; i++) {
            var request = new MockHttpServletRequest("POST", "/auth/forgot-password");
            request.setServletPath("/auth/forgot-password");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "192.0.2." + i);
            request.addHeader("X-Real-IP", "192.0.2." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
            if (i == 20) {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isEqualTo("900");
            }
        }
        assertThat(calls.get()).isEqualTo(20);
    }
    @Test void webhookFailsClosedWithoutConfiguredSecret() throws Exception {
        var filter = new RequestSecurityFilter("https://app.example", "", false);
        var request = new MockHttpServletRequest("POST", "/olist/estoque/webhook");
        request.setServletPath("/olist/estoque/webhook");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Unauthorized webhook accepted"); });
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
