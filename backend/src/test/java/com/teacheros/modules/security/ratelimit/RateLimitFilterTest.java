package com.teacheros.modules.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private MockHttpServletResponse hit(RateLimitFilter filter, String path, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test
    void enquiryEndpointIsLimitedWithRetryAfterAndStandardErrorBody() throws Exception {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnquiryPerWindow(3);
        RateLimitFilter filter = new RateLimitFilter(new InMemoryApiRateLimiter(), props,
                new ObjectMapper().findAndRegisterModules());

        for (int i = 0; i < 3; i++) {
            assertThat(hit(filter, "/api/v1/public/enquiries", "1.2.3.4").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = hit(filter, "/api/v1/public/enquiries", "1.2.3.4");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("RATE_LIMITED").contains("\"status\":429");

        // a different IP is unaffected
        assertThat(hit(filter, "/api/v1/public/enquiries", "9.9.9.9").getStatus()).isEqualTo(200);
    }

    @Test
    void nonApiPathsAreNotCounted() throws Exception {
        RateLimitProperties props = new RateLimitProperties();
        props.setIpPerWindow(1);
        RateLimitFilter filter = new RateLimitFilter(new InMemoryApiRateLimiter(), props,
                new ObjectMapper().findAndRegisterModules());
        for (int i = 0; i < 5; i++) {
            assertThat(hit(filter, "/actuator/health", "1.2.3.4").getStatus()).isEqualTo(200);
        }
    }
}
