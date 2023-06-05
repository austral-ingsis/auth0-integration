package edu.austral.ingsis.authexample

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Auth0IntegrationApplication

fun main(args: Array<String>) {
    runApplication<Auth0IntegrationApplication>(*args)
}
