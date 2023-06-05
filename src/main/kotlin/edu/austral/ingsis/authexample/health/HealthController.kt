package edu.austral.ingsis.authexample.health

import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/health")
class HealthController {
    @GetMapping("/readiness")
    suspend fun getReadiness(): ResponseEntity<String> = ok("READY")

    @GetMapping("/status")
    suspend fun getStatus(): ResponseEntity<String> = ok("OK")
}
