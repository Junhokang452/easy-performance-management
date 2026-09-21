package com.easyperformance.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductRequestExceptionHandlerTest {
    @Test
    void oversizedUploadReturnsLocalizedErrorCodeInsteadOfServerFailure() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EnumController())
            .setControllerAdvice(new ProductRequestExceptionHandler()).build();
        mvc.perform(post("/test/upload-too-large"))
            .andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("E9804261"));
    }
    @Test
    void invalidEnumRequestBody_returnsStandardBadRequest() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EnumController())
            .setControllerAdvice(new ProductRequestExceptionHandler()).build();

        mvc.perform(post("/test/enum").contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"INVALID\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("E0040001"))
            .andExpect(jsonPath("$.path").value("/test/enum"));
    }

    enum Value { VALID }
    record EnumRequest(Value value) {}

    @RestController
    @RequestMapping("/test")
    static class EnumController {
        @PostMapping("/upload-too-large")
        void uploadTooLarge() {
            throw new MaxUploadSizeExceededException(20L * 1024 * 1024);
        }
        @PostMapping("/enum")
        EnumRequest accept(@RequestBody EnumRequest request) { return request; }
    }
}
