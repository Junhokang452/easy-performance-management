/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.common;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * UUIDv7 (RFC 9562, 시간정렬) PK 생성 — 도메인 entity 전용.
 *
 * <p>lib 의 {@code com.easyware.platform.UuidV7} 은 control-plane 행 (platform_tenant /
 * tenant_product_db ...) 전용 — 본 클래스는 자매품 도메인 entity 전용으로 레이어 분리.
 *
 * <p>표준: easy-standards 01-identity-and-data §1 + 10-appendix-spring-jpa/persistence §1
 * (신규 PK = UUIDv7, {@code UUID.randomUUID()} (v4) 금지).
 *
 * <p>사용:
 * <pre>
 *   {@literal @}PrePersist
 *   public void prePersist() {
 *       if (this.id == null) this.id = UuidV7.generate();
 *   }
 * </pre>
 */
public final class UuidV7 {

    private UuidV7() {
    }

    /** 시간정렬 UUIDv7 1개 생성. */
    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
