package org.austral.ingsis.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class OwnedSnippet(val id: String, val ownerId: String, val content: String)

/**
 * Authorization based on an attribute of the resource: only the snippet's owner can read it.
 * The scope can't express this: it says what the app can do on the API, not which snippet.
 */
@RestController
class OwnedSnippetController {
    // In memory: enough for the demo
    private val snippets = ConcurrentHashMap<String, OwnedSnippet>()

    @PostMapping("/owned-snippets")
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody content: String,
    ): OwnedSnippet {
        // The owner is whoever created it: the token's "sub"
        val snippet = OwnedSnippet(UUID.randomUUID().toString(), jwt.subject!!, content)
        snippets[snippet.id] = snippet
        return snippet
    }

    @GetMapping("/owned-snippets/{id}")
    fun read(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: String,
    ): ResponseEntity<OwnedSnippet> {
        val snippet = snippets[id] ?: return ResponseEntity.notFound().build()

        // The rule: if your id is the owner's id, you can read it
        if (snippet.ownerId != jwt.subject) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(snippet)
    }
}
