package com.aaron.cloud.identity;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@ConditionalOnProperty(name = "ai.providers.auth", havingValue = "jwt-local")
public class JwtLocalSigningConfiguration {

    @Bean
    public JwtDecoder jwtDecoderLocal(@Value("${ai.auth.jwt-local.secret}") String secret) {
        SecretKey key = hmacKey(secret);
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoderLocal(@Value("${ai.auth.jwt-local.secret}") String secret) throws Exception {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        OctetSequenceKey jwk =
                new OctetSequenceKey.Builder(bytes).algorithm(JWSAlgorithm.HS256).keyID("ai-local-hs256").build();
        JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwks);
    }

    private static SecretKey hmacKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("ai.auth.jwt-local.secret 长度须 >= 32 字节");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
