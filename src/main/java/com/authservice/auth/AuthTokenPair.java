package com.authservice.auth;

record AuthTokenPair(String accessToken, String refreshToken, long expiresIn) {}
