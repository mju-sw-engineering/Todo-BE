package com.todo.domain.todo.recommendation.holiday;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 공공데이터포털 한국천문연구원 특일정보 {@code getRestDeInfo} 호출만 담당한다.
 * 캐시·실패 대체는 모르며, 그것은 {@link DataGoKrHolidayProvider}의 일이다.
 *
 * <p>{@code getRestDeInfo}를 쓰는 이유: 다섯 오퍼레이션 중 공휴일(대체공휴일 포함)만 준다.
 * 기념일({@code getAnniversaryInfo})·절기({@code get24DaysInfo})는 쉬는 날이 아니다.
 */
@Slf4j
@Component
public class HolidayApiClient {

    private static final String SUCCESS_CODE = "00";
    private static final int ROWS_PER_MONTH = 50;
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final HolidayApiProperties properties;
    private final ObjectMapper objectMapper;

    public HolidayApiClient(
            @Qualifier("holidayRestClient") RestClient restClient,
            HolidayApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 한 달의 공휴일을 가져온다. {@code isHoliday=N}인 항목(기념일 성격)은 제외한다.
     *
     * @throws HolidayApiException 연결 실패·타임아웃·비정상 resultCode·파싱 실패
     */
    public List<Holiday> fetchHolidays(YearMonth month) {
        if (!properties.hasServiceKey()) {
            throw new HolidayApiException("공휴일 API 서비스 키가 설정되지 않았습니다. HOLIDAY_API_SERVICE_KEY를 확인하세요.");
        }

        String body;
        try {
            body = restClient.get()
                    .uri(buildUri(month))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new HolidayApiException("공휴일 API가 오류를 반환했습니다. status=" + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new HolidayApiException("공휴일 API 연결에 실패했거나 타임아웃됐습니다.", e);
        }

        if (body == null || body.isBlank()) {
            throw new HolidayApiException("공휴일 API 응답이 비어 있습니다.");
        }
        return parse(body, month);
    }

    /**
     * 서비스 키는 base64라 {@code +}, {@code /}, {@code =}가 들어간다. 이 문자들은 쿼리에서 "허용"
     * 문자라 {@code UriComponentsBuilder.build()}·{@code encode()} 모두 그대로 두고, 그러면 포털은
     * {@code +}를 공백으로 읽어 {@code SERVICE_KEY_IS_NOT_REGISTERED}를 돌려준다. 그래서 키만
     * {@link UriUtils#encode}로 예약 문자까지 전부 인코딩한 뒤 {@code build(true)}로 "이미 인코딩됨"을
     * 선언한다. 나머지 파라미터는 숫자와 ASCII라 그대로 안전하다.
     *
     * <p>이 때문에 설정에는 <b>Decoding 키</b>를 넣어야 한다. Encoding 키를 넣으면 여기서 한 번 더
     * 인코딩돼 {@code %2B}가 {@code %252B}가 된다.
     *
     * <p>{@code solMonth}는 반드시 두 자리다. {@code "8"}을 보내면 포털이 빈 결과를 준다.
     */
    private URI buildUri(YearMonth month) {
        return UriComponentsBuilder.fromUriString(properties.restDeInfoUrl())
                .queryParam("serviceKey", UriUtils.encode(properties.serviceKey(), StandardCharsets.UTF_8))
                .queryParam("solYear", month.getYear())
                .queryParam("solMonth", String.format("%02d", month.getMonthValue()))
                .queryParam("numOfRows", ROWS_PER_MONTH)
                .queryParam("pageNo", 1)
                .queryParam("_type", "json")
                .build(true)
                .toUri();
    }

    /**
     * 응답 모양이 건수에 따라 달라진다 — 0건이면 {@code items}가 빈 문자열 {@code ""}, 1건이면
     * {@code item}이 배열이 아니라 객체, 2건 이상이면 배열. DTO 바인딩 대신 {@link JsonNode}로
     * 세 경우를 직접 가른다.
     *
     * <p>키 오류 같은 게이트웨이 거절은 {@code response} 대신 {@code OpenAPI_ServiceResponse.cmmMsgHeader}
     * 껍데기로 온다 (실측: {@code returnReasonCode=20 SERVICE_KEY_IS_NULL}, {@code 30
     * SERVICE_KEY_IS_NOT_REGISTERED_ERROR}). 발급 직후 활성화 전에도 30이 난다.
     */
    private List<Holiday> parse(String body, YearMonth month) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new HolidayApiException("공휴일 API 응답을 JSON으로 읽지 못했습니다.", e);
        }

        JsonNode gatewayError = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (!gatewayError.isMissingNode()) {
            throw new HolidayApiException("공휴일 API 게이트웨이가 거절했습니다. returnReasonCode="
                    + gatewayError.path("returnReasonCode").asText("") + " ("
                    + gatewayError.path("errMsg").asText("") + ")");
        }

        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asText("");
        if (!SUCCESS_CODE.equals(resultCode)) {
            String resultMsg = response.path("header").path("resultMsg").asText("");
            throw new HolidayApiException("공휴일 API resultCode=" + resultCode + " (" + resultMsg + ")");
        }

        JsonNode items = response.path("body").path("items");
        if (!items.isObject()) {
            // 0건: "items": ""
            return List.of();
        }
        JsonNode item = items.path("item");
        List<Holiday> holidays = new ArrayList<>();
        if (item.isArray()) {
            item.forEach(node -> addIfHoliday(holidays, node, month));
        } else if (item.isObject()) {
            addIfHoliday(holidays, item, month);
        }
        holidays.sort((a, b) -> a.date().compareTo(b.date()));
        return List.copyOf(holidays);
    }

    private void addIfHoliday(List<Holiday> holidays, JsonNode node, YearMonth month) {
        if (!"Y".equals(node.path("isHoliday").asText(""))) {
            return;
        }
        String locdate = node.path("locdate").asText("");
        LocalDate date;
        try {
            date = LocalDate.parse(locdate, LOCDATE);
        } catch (DateTimeParseException e) {
            log.warn("공휴일 API 항목의 날짜를 읽지 못해 건너뜁니다. month={}, locdate={}", month, locdate);
            return;
        }
        holidays.add(new Holiday(date, node.path("dateName").asText("").strip()));
    }
}
