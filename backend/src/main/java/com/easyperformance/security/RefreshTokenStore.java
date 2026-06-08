/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 저장소 — 단계 3 BE-CC-2 JWT 5분리 (G84 D=A, Task #122, 2026-06-08).
 *
 * <p>그린필드 dev 진입 — in-memory ConcurrentHashMap + {@link Clock} 주입 (테스트 친화). 인스턴스 재시작 시
 * 폐기 (LIVE 미운영 / dev only). production 격상 시 RDB
 * (refresh_token 테이블 + tenant_id + user_id + token_hash + expires_at + revoked_at) 또는 Redis 로
 * 교체 (본 클래스 인터페이스 유지 — 호출자 무영향).
 *
 * <p><strong>책임</strong>
 * <ul>
 *   <li>refresh 토큰 발행 시 (tokenKey → RefreshSession) 저장.</li>
 *   <li>refresh 토큰 검증 시 (tokenKey 존재 + 미폐기 + 미만료) 확인.</li>
 *   <li>logout / refresh rotation 시 폐기 (revoke).</li>
 * </ul>
 *
 * <p><strong>모범 정합 (3 자매품 누적)</strong>
 * <ul>
 *   <li>jobeval `4dff03a` (Java 그린필드) — in-memory ConcurrentHashMap + token 본문 key.</li>
 *   <li>mra `38e566d` (Java dual-claim 비파괴) — 기존 LoginService 보존 + AuthService 신규.</li>
 *   <li>jobstructure `d64944e` (Kotlin idiomatic) — Clock 주입 + jti claim (iat 충돌 방지).</li>
 *   <li>performance 본 슬라이스 = Java 그린필드 모범 4호 + Clock 주입 (jobstructure 추종).</li>
 * </ul>
 */
@Component
public class RefreshTokenStore {

    private final Map<String, RefreshSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public RefreshTokenStore() {
        this(Clock.systemUTC());
    }

    /** 테스트 친화 — 가짜 Clock 주입. */
    public RefreshTokenStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * refresh 토큰 발행 시 세션 등록.
     *
     * @param tokenKey refresh 토큰 본문 (또는 jti — 단계 3+ 격상 시)
     * @param userId   대상 사용자 UUID
     * @param tenantId 테넌트 UUID (B2B) — B2C 의 경우 null (단, performance 는 ADR-031 B2C 부재)
     * @param expiresAt 만료 시각
     */
    public void register(String tokenKey, UUID userId, UUID tenantId, Instant expiresAt) {
        Objects.requireNonNull(tokenKey, "tokenKey");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        sessions.put(tokenKey, new RefreshSession(userId, tenantId, expiresAt, false));
    }

    /** 토큰 유효성 검증 — 존재 + 미폐기 + 미만료 모두 true 면 세션 반환. */
    public RefreshSession validate(String tokenKey) {
        RefreshSession session = sessions.get(tokenKey);
        if (session == null) {
            return null;
        }
        if (session.revoked()) {
            return null;
        }
        if (session.expiresAt().isBefore(Instant.now(clock))) {
            sessions.remove(tokenKey);
            return null;
        }
        return session;
    }

    /** refresh rotation 또는 logout 시 폐기 — 키 제거 (재사용 차단). */
    public void revoke(String tokenKey) {
        sessions.remove(tokenKey);
    }

    /** 사용자 전체 폐기 — 비밀번호 변경 / 강제 로그아웃 시. */
    public void revokeAllForUser(UUID userId) {
        sessions.entrySet().removeIf(e -> Objects.equals(e.getValue().userId(), userId));
    }

    /** 만료된 세션 청소 (dev 인메모리 누수 방지). 운영 격상 시 스케줄러 또는 RDB TTL 로 대체. */
    public void cleanupExpired() {
        Instant now = Instant.now(clock);
        sessions.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    /** 현재 등록된 세션 수 (모니터링 / 테스트용). */
    public int size() {
        return sessions.size();
    }

    /** 인메모리 세션 메타데이터. */
    public record RefreshSession(UUID userId, UUID tenantId, Instant expiresAt, boolean revoked) {}
}
