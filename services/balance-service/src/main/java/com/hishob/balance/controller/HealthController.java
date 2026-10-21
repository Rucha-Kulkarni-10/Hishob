package com.hishob.balance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    // Balance Service is a stateless aggregator with no datasource of its own, so there is no
    // storage dependency to verify. Probing its downstream clients here would make every
    // deployment health check depend on other services being up.
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "balance-service"
        ));
    }
}
