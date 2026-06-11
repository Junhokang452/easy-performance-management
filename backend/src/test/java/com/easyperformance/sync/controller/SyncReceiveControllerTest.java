/*
 * Copyright 2026 easy-performance-management contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.easyperformance.sync.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.easyperformance.sync.dto.SyncDtos.CoreMasterBatchRequest;
import com.easyperformance.sync.dto.SyncDtos.CoreMasterBatchResponse;
import com.easyperformance.sync.service.ReadModelSyncService;
import com.easyware.platform.error.ApiException;
import com.easyware.platform.hmac.HmacService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SyncReceiveController 인증 가드 단위 테스트 (P0-S6, talent SyncReceiveControllerTest 정합) —
 * 미설정 503(E9805301) / bearer·HMAC 401(E9804105) / 파싱 400(E9804005) / 정상 위임.
 * Spring 컨텍스트 불요 (직접 생성).
 */
class SyncReceiveControllerTest {

    private static final String HCM_BEARER = "hcm-bearer-token-for-test";
    private static final String HCM_SECRET = "0123456789abcdef0123456789abcdef";          // 32 chars
    private static final String CORE_MASTER_BODY = "{\"employees\":[],\"orgUnits\":[],\"assignments\":[]}";

    private final ReadModelSyncService syncService = mock(ReadModelSyncService.class);

    private SyncReceiveController configured() {
        return new SyncReceiveController(syncService, new ObjectMapper(), HCM_BEARER, HCM_SECRET);
    }

    private static String sign(String secret, String body) {
        return new HmacService(secret).compute(body);
    }

    @Test
    void rejectsWhenChannelNotConfigured() {
        SyncReceiveController controller = new SyncReceiveController(
            syncService, new ObjectMapper(), "", "");

        assertThatThrownBy(() -> controller.receiveCoreMaster(CORE_MASTER_BODY, "Bearer x", "sig"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode().code()).isEqualTo("E9805301"));
    }

    @Test
    void rejectsWhenOnlyBearerConfiguredButSecretMissing() {
        // secret 미설정 → HmacService 미생성 → 미설정 503 (bearer 단독으로는 활성화 불가)
        SyncReceiveController controller = new SyncReceiveController(
            syncService, new ObjectMapper(), HCM_BEARER, "");

        assertThatThrownBy(() -> controller.receiveCoreMaster(
            CORE_MASTER_BODY, "Bearer " + HCM_BEARER, "sig"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode().code()).isEqualTo("E9805301"));
    }

    @Test
    void rejectsWrongBearer() {
        SyncReceiveController controller = configured();

        assertThatThrownBy(() -> controller.receiveCoreMaster(
            CORE_MASTER_BODY, "Bearer wrong-token", sign(HCM_SECRET, CORE_MASTER_BODY)))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode().code()).isEqualTo("E9804105"));
    }

    @Test
    void rejectsMissingAuthorizationHeader() {
        SyncReceiveController controller = configured();

        assertThatThrownBy(() -> controller.receiveCoreMaster(
            CORE_MASTER_BODY, null, sign(HCM_SECRET, CORE_MASTER_BODY)))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode().code()).isEqualTo("E9804105"));
    }

    @Test
    void rejectsBadSignature() {
        SyncReceiveController controller = configured();

        assertThatThrownBy(() -> controller.receiveCoreMaster(
            CORE_MASTER_BODY, "Bearer " + HCM_BEARER, "deadbeef"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode().code()).isEqualTo("E9804105"));
    }

    @Test
    void rejectsMissingSignatureHeader() {
        SyncReceiveController controller = configured();

        assertThatThrownBy(() -> controller.receiveCoreMaster(
            CORE_MASTER_BODY, "Bearer " + HCM_BEARER, null))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode().code()).isEqualTo("E9804105"));
    }

    @Test
    void delegatesToServiceOnValidAuth() {
        SyncReceiveController controller = configured();
        CoreMasterBatchResponse expected = new CoreMasterBatchResponse(0, 0, 0, 0, 0, 0);
        when(syncService.applyCoreMaster(any(CoreMasterBatchRequest.class))).thenReturn(expected);

        ResponseEntity<CoreMasterBatchResponse> response = controller.receiveCoreMaster(
            CORE_MASTER_BODY, "Bearer " + HCM_BEARER, sign(HCM_SECRET, CORE_MASTER_BODY));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(syncService).applyCoreMaster(any(CoreMasterBatchRequest.class));
    }

    @Test
    void rejectsUnparseablePayloadAfterAuth() {
        SyncReceiveController controller = configured();
        String broken = "not-json{";

        assertThatThrownBy(() -> controller.receiveCoreMaster(
            broken, "Bearer " + HCM_BEARER, sign(HCM_SECRET, broken)))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode().code()).isEqualTo("E9804005"));
    }
}
