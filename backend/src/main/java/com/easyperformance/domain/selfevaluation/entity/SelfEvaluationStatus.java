/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.domain.selfevaluation.entity;

/** 자기평가 상태 머신. */
public enum SelfEvaluationStatus {
    DRAFT,
    SUBMITTED,
    REVIEWED,
    FINALIZED
}
