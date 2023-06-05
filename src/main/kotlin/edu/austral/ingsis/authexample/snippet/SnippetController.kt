package edu.austral.ingsis.authexample.snippet

import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/snippets")
class SnippetController {
    val snippets = mutableMapOf<String, Snippet>(
        Pair("asd", Snippet("asd", "Best Snippet", "Some code")),
        Pair("qwe", Snippet("qwe", "Best Snippet", "Some code")),
    )

    @GetMapping
    @ResponseStatus(OK)
    suspend fun listSnippets(): List<Snippet> = snippets.values.toList()

    @PostMapping
    @ResponseStatus(CREATED)
    suspend fun createSnippet(@RequestBody snippet: Snippet): Snippet {
        return snippets[snippet.id]
            ?.let { throw ResponseStatusException(CONFLICT, "Snippet already exists") }
            ?: run {
                snippets[snippet.id] = snippet
                snippet
            }
    }

    @GetMapping("/{id}")
    @ResponseStatus(OK)
    suspend fun getSnippet(@PathVariable id: String): Snippet {
        return snippets[id] ?: throw ResponseStatusException(NOT_FOUND, "Snippet with id '$id' not found")
    }
}
