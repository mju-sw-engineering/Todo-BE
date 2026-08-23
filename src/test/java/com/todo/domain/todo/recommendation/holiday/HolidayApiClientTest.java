package com.todo.domain.todo.recommendation.holiday;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class HolidayApiClientTest {

    private static final String BASE_URL = "https://apis.example.test/SpcdeInfoService";

    private MockRestServiceServer server;
    private HolidayApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HolidayApiClient(builder.build(), properties("decoded+key/with=specials"), new ObjectMapper());
    }

    @Test
    void 여러_건이면_배열을_읽어_공휴일만_날짜순으로_돌려준다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL + "/getRestDeInfo")))
                .andExpect(method(GET))
                .andExpect(queryParam("solYear", "2026"))
                .andExpect(queryParam("solMonth", "10"))
                .andExpect(queryParam("_type", "json"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                         "body":{"items":{"item":[
                           {"dateKind":"01","dateName":"한글날","isHoliday":"Y","locdate":20261009,"seq":1},
                           {"dateKind":"01","dateName":"개천절","isHoliday":"Y","locdate":20261003,"seq":1},
                           {"dateKind":"01","dateName":"기념일","isHoliday":"N","locdate":20261015,"seq":1}
                         ]},"numOfRows":50,"pageNo":1,"totalCount":3}}}
                        """, MediaType.APPLICATION_JSON));

        List<Holiday> holidays = client.fetchHolidays(YearMonth.of(2026, 10));

        assertThat(holidays).containsExactly(
                new Holiday(LocalDate.of(2026, 10, 3), "개천절"),
                new Holiday(LocalDate.of(2026, 10, 9), "한글날"));
        server.verify();
    }

    @Test
    void 한_건이면_item이_객체로_와도_읽는다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                         "body":{"items":{"item":
                           {"dateKind":"01","dateName":"광복절","isHoliday":"Y","locdate":20260815,"seq":1}
                         },"numOfRows":50,"pageNo":1,"totalCount":1}}}
                        """, MediaType.APPLICATION_JSON));

        List<Holiday> holidays = client.fetchHolidays(YearMonth.of(2026, 8));

        assertThat(holidays).containsExactly(new Holiday(LocalDate.of(2026, 8, 15), "광복절"));
    }

    @Test
    void 공휴일이_없는_달은_items가_빈_문자열로_오고_빈_목록을_돌려준다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
                         "body":{"items":"","numOfRows":50,"pageNo":1,"totalCount":0}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchHolidays(YearMonth.of(2026, 11))).isEmpty();
    }

    @Test
    void 서비스_키는_한_번만_인코딩되고_solMonth는_두_자리다() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("serviceKey=decoded%2Bkey%2Fwith%3Dspecials")))
                .andExpect(queryParam("solMonth", "08"))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"00","resultMsg":"OK"},"body":{"items":""}}}
                        """, MediaType.APPLICATION_JSON));

        client.fetchHolidays(YearMonth.of(2026, 8));

        server.verify();
    }

    @Test
    void resultCode가_정상이_아니면_예외다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"},"body":{}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchHolidays(YearMonth.of(2026, 8)))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("resultCode=30");
    }

    @Test
    void 게이트웨이가_키_오류로_거절하면_사유를_담은_예외다() {
        // 2026-08-23 실측 응답. response 껍데기가 아니라 OpenAPI_ServiceResponse로 온다.
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                          "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                          "returnAuthMsg":"등록되지 않은 서비스키",
                          "returnReasonCode":"30"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchHolidays(YearMonth.of(2026, 8)))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("returnReasonCode=30")
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    void JSON이_아닌_응답은_파싱_실패_예외다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withSuccess("""
                        <OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
                        """, MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> client.fetchHolidays(YearMonth.of(2026, 8)))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    void HTTP_오류_응답은_예외다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.fetchHolidays(YearMonth.of(2026, 8)))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("503");
    }

    @Test
    void 타임아웃은_예외다() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE_URL)))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.fetchHolidays(YearMonth.of(2026, 8)))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("타임아웃");
    }

    @Test
    void 서비스_키가_없으면_호출하지_않고_예외다() {
        HolidayApiClient withoutKey = new HolidayApiClient(RestClient.create(), properties(""), new ObjectMapper());

        assertThatThrownBy(() -> withoutKey.fetchHolidays(YearMonth.of(2026, 8)))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("HOLIDAY_API_SERVICE_KEY");
    }

    private HolidayApiProperties properties(String serviceKey) {
        return new HolidayApiProperties(serviceKey, BASE_URL + "/", Duration.ofSeconds(1), Duration.ofSeconds(1));
    }
}
