package com.easyperformance.config;

import com.easyperformance.domain.evaluationcycle.entity.CycleStatus;
import com.easyperformance.error.ProductRequestExceptionHandler;
import com.easyware.platform.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot 4.1.1 compatibility characterization for the suite's transitional Jackson 2 public wire API.
 *
 * <p>This is an actual MVC slice: it checks the converter selected by Spring MVC, then exercises the
 * same converter through MockMvc for response and request bodies. It must remain on
 * {@code com.fasterxml.jackson.*} until the suite performs its separately planned Jackson 3 cutover.
 */
@WebMvcTest(
    controllers = Jackson2MvcCompatibilityTest.WireController.class,
    properties = "spring.http.converters.preferred-json-mapper=jackson2"
)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {
    Jackson2MvcCompatibilityTest.WireController.class,
    ProductRequestExceptionHandler.class
})
class Jackson2MvcCompatibilityTest {

    @Test
    void embeddedServerUsesSecurityPatchedTomcat() {
        assertThat(org.apache.catalina.util.ServerInfo.getServerInfo()).isEqualTo("Apache Tomcat/11.0.25");
    }

    private static final UUID ID = UUID.fromString("0199291a-7000-8000-0000-000000000001");
    private static final Instant GENERATED_AT = Instant.parse("2026-09-07T01:02:03.456Z");
    private static final LocalDate PERIOD_START = LocalDate.parse("2026-09-07");
    private static final BigDecimal SCORE = new BigDecimal("1234567890.123456789");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Test
    void baselineUsesBoot411AndSelectsFasterXmlJackson2ForJson() {
        assertThat(SpringBootVersion.getVersion()).isEqualTo("4.1.1");

        HttpMessageConverter<?> selectedJsonConverter = handlerAdapter.getMessageConverters().stream()
            .filter(converter -> converter.canWrite(WireContract.class, MediaType.APPLICATION_JSON))
            .findFirst()
            .orElseThrow();

        assertThat(selectedJsonConverter).isInstanceOf(MappingJackson2HttpMessageConverter.class);
        MappingJackson2HttpMessageConverter jackson2 =
            (MappingJackson2HttpMessageConverter) selectedJsonConverter;
        assertThat(jackson2.getObjectMapper()).isSameAs(objectMapper);
        assertThat(objectMapper.getClass().getName()).startsWith("com.fasterxml.jackson.databind.");
    }

    @Test
    void responsePreservesNullableIsoDatesBigDecimalAndEnumWireShape() throws Exception {
        String body = mvc.perform(get("/test/jackson2/wire"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(ID.toString()))
            .andExpect(jsonPath("$.optionalNote").value(nullValue()))
            .andExpect(jsonPath("$.generatedAt").value("2026-09-07T01:02:03.456Z"))
            .andExpect(jsonPath("$.periodStart").value("2026-09-07"))
            .andExpect(content().string(containsString("\"score\":1234567890.123456789")))
            .andExpect(jsonPath("$.status").value("FINALIZED"))
            .andReturn().getResponse().getContentAsString();

        WireContract decoded = objectMapper.readValue(body, WireContract.class);
        assertThat(decoded).isEqualTo(expectedContract());
    }

    @Test
    void requestBodyUsesTheSameJackson2WireContract() throws Exception {
        String request = """
            {
              "id": "0199291a-7000-8000-0000-000000000001",
              "optionalNote": null,
              "generatedAt": "2026-09-07T01:02:03.456Z",
              "periodStart": "2026-09-07",
              "score": 1234567890.123456789,
              "status": "FINALIZED"
            }
            """;

        String body = mvc.perform(post("/test/jackson2/wire")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"score\":1234567890.123456789")))
            .andExpect(jsonPath("$.optionalNote").value(nullValue()))
            .andExpect(jsonPath("$.status").value("FINALIZED"))
            .andReturn().getResponse().getContentAsString();

        WireContract decoded = objectMapper.readValue(body, WireContract.class);
        assertThat(decoded).isEqualTo(expectedContract());
    }

    @Test
    void malformedEnumKeepsTheExistingStandardApiErrorEnvelope() throws Exception {
        String body = mvc.perform(post("/test/jackson2/wire")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": "0199291a-7000-8000-0000-000000000001",
                      "optionalNote": null,
                      "generatedAt": "2026-09-07T01:02:03.456Z",
                      "periodStart": "2026-09-07",
                      "score": 1.00,
                      "status": "NOT_A_STATUS"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("E0040001"))
            .andExpect(jsonPath("$.message").value("Invalid request body"))
            .andExpect(jsonPath("$.messageKey").value("error.E0040001"))
            .andExpect(jsonPath("$.details").isEmpty())
            .andExpect(jsonPath("$.traceId").value(nullValue()))
            .andExpect(jsonPath("$.path").value("/test/jackson2/wire"))
            .andExpect(jsonPath("$.timestamp").isString())
            .andReturn().getResponse().getContentAsString();

        ApiError error = objectMapper.readValue(body, ApiError.class);
        assertThat(error.timestamp()).isNotNull();
    }

    private static WireContract expectedContract() {
        return new WireContract(ID, null, GENERATED_AT, PERIOD_START, SCORE, CycleStatus.FINALIZED);
    }

    record WireContract(
        UUID id,
        String optionalNote,
        Instant generatedAt,
        LocalDate periodStart,
        BigDecimal score,
        CycleStatus status
    ) {}

    @RestController
    @RequestMapping("/test/jackson2")
    static class WireController {

        @GetMapping("/wire")
        WireContract getWireContract() {
            return expectedContract();
        }

        @PostMapping("/wire")
        WireContract echo(@RequestBody WireContract request) {
            return request;
        }
    }
}
