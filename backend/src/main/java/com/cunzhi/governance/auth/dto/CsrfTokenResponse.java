package com.cunzhi.governance.auth.dto;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(
        String token,
        String headerName,
        String parameterName
) {
    public static CsrfTokenResponse from(CsrfToken token) {
        return new CsrfTokenResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }
}
