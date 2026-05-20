package com.nexura.app.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtils {

    @Value("${nexura.jwt.secret}")
    private String jwtSecret;

    @Value("${nexura.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public String generateJwtToken(String email) {
        return JWT.create()
                .withSubject(email)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date((new Date()).getTime() + jwtExpirationMs))
                .sign(Algorithm.HMAC256(jwtSecret));
    }

    public String getEmailFromJwtToken(String token) {
        DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(jwtSecret))
                .build()
                .verify(token);
        return decodedJWT.getSubject();
    }

    public boolean validateJwtToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
