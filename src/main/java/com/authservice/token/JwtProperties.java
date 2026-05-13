package com.authservice.token;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    private Resource privateKey;
    private Resource publicKey;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;
}
