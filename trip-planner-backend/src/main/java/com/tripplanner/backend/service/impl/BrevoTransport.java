package com.tripplanner.backend.service.impl;

import java.io.IOException;

public interface BrevoTransport {
    int post(String uri, String apiKey, String jsonBody) throws IOException, InterruptedException;
}
