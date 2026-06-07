/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance;

import com.easyperformance.common.UuidV7;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UuidV7 단위 테스트 — Spring 컨텍스트 미부팅.
 *
 * <p>단계 1 BE-CC-1 진입 — DB 없는 환경에서도 검증 가능한 최소 단위 테스트. ContextLoads 는 PG 의존이
 * 있으므로 별도 통합 테스트로 분리.
 */
class UuidV7Test {

    @Test
    void generate_returnsVersion7Uuid() {
        UUID u = UuidV7.generate();
        assertNotNull(u);
        // UUIDv7 version nibble = 7 (variant bits 0b10 in clock_seq_hi)
        assertEquals(7, u.version(), "UUIDv7 expected, got version " + u.version());
    }

    @Test
    void generate_producesUniqueValues() {
        UUID a = UuidV7.generate();
        UUID b = UuidV7.generate();
        assertNotEquals(a, b);
    }
}
