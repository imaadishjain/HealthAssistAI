package com.example.HealthAssistBackend.controller;

import com.example.HealthAssistBackend.model.ChatRequest;
import com.example.HealthAssistBackend.model.ChatResponse;
import com.example.HealthAssistBackend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Chat controller providing sync, async (streaming), and agentic endpoints.
 */
@RestController
@RequestMapping("/ai")
@CrossOrigin(origins = "http://localhost:4200")
@Tag(name = "Chat", description = "Conversational AI endpoints — sync, streaming & agentic")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Synchronous chat", description = "Full RAG + tool-calling response with guardrails")
    @PostMapping("/chat/sync")
    public ResponseEntity<ChatResponse> chatSync(@Valid @RequestBody ChatRequest request) {
        log.info("POST /ai/chat/sync - message length: {}", request.message().length());
        ChatResponse response = chatService.chatSync(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Streaming chat (SSE)", description = "Real-time token-by-token response via Server-Sent Events")
    @GetMapping(value = "/chat/async", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, String>>> chatAsync(@RequestParam String message) {
        log.info("GET /ai/chat/async - message length: {}", message.length());
        return chatService.chatStream(message)
            .map(token -> ServerSentEvent.<Map<String, String>>builder()
                .data(Map.of("token", token))
                .build());
    }

    @Operation(summary = "Agentic health chat", description = "Full orchestrator pipeline — intent routing, multi-agent workflow")
    @PostMapping("/agent/health")
    public ResponseEntity<ChatResponse> agentHealth(@Valid @RequestBody ChatRequest request) {
        log.info("POST /ai/agent/health - message length: {}", request.message().length());
        ChatResponse response = chatService.agentChat(request);
        return ResponseEntity.ok(response);
    }
}
