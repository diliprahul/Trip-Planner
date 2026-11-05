package com.tripplanner.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Temporary, one-time TCP reachability check for the configured SMTP host. */
@Component
public class SmtpConnectivityDiagnostic implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SmtpConnectivityDiagnostic.class);
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    @Override
    public void run(ApplicationArguments args) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("smtp.gmail.com", 587), CONNECT_TIMEOUT_MILLIS);
            log.info("SMTP connectivity diagnostic: SUCCESS");
        } catch (IOException exception) {
            log.warn("SMTP connectivity diagnostic: FAILED exceptionType={}", exception.getClass().getSimpleName());
        }
    }
}
