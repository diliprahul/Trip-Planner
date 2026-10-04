package com.tripplanner.backend.service;

import com.tripplanner.backend.dto.WeatherForecastDto;
import com.tripplanner.backend.dto.WeatherResponseDto;
import com.tripplanner.backend.service.impl.WeatherServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherServiceImplTest {

    private RestTemplate restTemplate;
    private WeatherServiceImpl weatherService;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        weatherService = new WeatherServiceImpl(restTemplate);
    }

    private Map<String, Object> createMockOpenMeteoResponse(List<String> dates, List<Integer> codes) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> daily = new HashMap<>();
        daily.put("time", dates);

        Double[] maxs = new Double[dates.size()];
        Double[] mins = new Double[dates.size()];
        Integer[] resolvedCodes = new Integer[dates.size()];
        Double[] precips = new Double[dates.size()];

        for (int i = 0; i < dates.size(); i++) {
            maxs[i] = 30.0 + i;
            mins[i] = 20.0 + i;
            resolvedCodes[i] = codes != null && i < codes.size() ? codes.get(i) : 0;
            precips[i] = 10.0 * i;
        }

        daily.put("temperature_2m_max", Arrays.asList(maxs));
        daily.put("temperature_2m_min", Arrays.asList(mins));
        daily.put("weathercode", Arrays.asList(resolvedCodes));
        daily.put("precipitation_probability_max", Arrays.asList(precips));

        response.put("daily", daily);
        return response;
    }

    private Map<String, Object> createMockOpenMeteoResponse(List<String> dates) {
        return createMockOpenMeteoResponse(dates, null);
    }

    @Test
    void testForecastDaysParamIncluded() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 6);

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(List.of("2026-10-06")));

        weatherService.getForecast(17.3850, 78.4867, start, end);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(urlCaptor.capture(), eq(Map.class));
        String requestedUrl = urlCaptor.getValue();

        assertTrue(requestedUrl.contains("forecast_days=16"), "URL must contain forecast_days=16");
        assertTrue(requestedUrl.contains("latitude=17.3850"), "URL must contain latitude");
        assertTrue(requestedUrl.contains("longitude=78.4867"), "URL must contain longitude");
    }

    @Test
    void test1DayTrip() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 6);

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(List.of("2026-10-06")));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.AVAILABLE, response.getStatus());
        assertNotNull(response.getForecasts());
        assertEquals(1, response.getForecasts().size());
        assertEquals("2026-10-06", response.getForecasts().get(0).getDate());
    }

    @Test
    void test5DayTrip() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 10);

        List<String> dates = List.of("2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(dates));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.AVAILABLE, response.getStatus());
        assertNotNull(response.getForecasts());
        assertEquals(5, response.getForecasts().size());
        assertEquals("2026-10-06", response.getForecasts().get(0).getDate());
        assertEquals("2026-10-10", response.getForecasts().get(4).getDate());
    }

    @Test
    void test7DayTrip() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 12);

        List<String> dates = List.of("2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10", "2026-10-11", "2026-10-12");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(dates));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.AVAILABLE, response.getStatus());
        assertEquals(7, response.getForecasts().size());
    }

    @Test
    void test10DayTrip() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 15);

        List<String> dates = List.of(
                "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10",
                "2026-10-11", "2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15"
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(dates));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.AVAILABLE, response.getStatus());
        assertEquals(10, response.getForecasts().size());
    }

    @Test
    void test15DayTrip() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 20);

        List<String> dates = List.of(
                "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09", "2026-10-10",
                "2026-10-11", "2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15",
                "2026-10-16", "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20"
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(dates));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.AVAILABLE, response.getStatus());
        assertEquals(15, response.getForecasts().size());
    }

    @Test
    void testTripPartiallyOutsideForecast() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 10);

        // Only 3 days available out of 5 requested
        List<String> dates = List.of("2026-10-06", "2026-10-07", "2026-10-08");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(dates));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.PARTIALLY_AVAILABLE, response.getStatus());
        assertEquals(3, response.getForecasts().size());
        assertEquals("2026-10-06", response.getForecasts().get(0).getDate());
        assertEquals("2026-10-08", response.getForecasts().get(2).getDate());
    }

    @Test
    void testApiFailure() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 10);

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RestClientException("Connection timed out"));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.API_ERROR, response.getStatus());
        assertNotNull(response.getMessage());
    }

    @Test
    void testDateMatchingNonSequentialOrUnordered() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 8);

        // Response contains dates in shuffled/unordered order or with gaps
        List<String> dates = List.of("2026-10-07", "2026-10-06", "2026-10-08");
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(dates));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.AVAILABLE, response.getStatus());
        assertEquals(3, response.getForecasts().size());
        // Verify chronological order in returned forecasts
        assertEquals("2026-10-06", response.getForecasts().get(0).getDate());
        assertEquals("2026-10-07", response.getForecasts().get(1).getDate());
        assertEquals("2026-10-08", response.getForecasts().get(2).getDate());
    }

    @Test
    void testWmoWeatherCodeMapping() {
        LocalDate start = LocalDate.of(2026, 10, 6);
        LocalDate end = LocalDate.of(2026, 10, 8);

        List<String> dates = List.of("2026-10-06", "2026-10-07", "2026-10-08");
        List<Integer> codes = List.of(0, 61, 95); // Clear sky, Rain, Thunderstorm

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(createMockOpenMeteoResponse(dates, codes));

        WeatherResponseDto response = weatherService.getForecast(17.3850, 78.4867, start, end);

        assertNotNull(response);
        assertEquals(WeatherResponseDto.Status.AVAILABLE, response.getStatus());
        assertEquals(3, response.getForecasts().size());

        assertEquals("Clear sky", response.getForecasts().get(0).getCondition());
        assertEquals("01d", response.getForecasts().get(0).getIcon());

        assertEquals("Rain", response.getForecasts().get(1).getCondition());
        assertEquals("10d", response.getForecasts().get(1).getIcon());

        assertEquals("Thunderstorm", response.getForecasts().get(2).getCondition());
        assertEquals("11d", response.getForecasts().get(2).getIcon());
    }
}
