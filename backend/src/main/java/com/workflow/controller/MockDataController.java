package com.workflow.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;

@RestController
@RequestMapping("/mock-data")
@CrossOrigin(origins = "*")
public class MockDataController {

    private static final Path BASE_DIR = Paths.get("src/main/resources/mock-data");
    private static final Set<String> ALLOWED_FILES = Set.of(
            "payment-request.json",
            "return-option.json",
            "users.json",
            "workflow.json");

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<String> getMockFile(@PathVariable String fileName) throws IOException {

        if (!ALLOWED_FILES.contains(fileName)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new ClassPathResource("mock-data/" + fileName);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
    }

    @PutMapping("/{fileName:.+}")
    public ResponseEntity<Void> putMockFile(@PathVariable String fileName, @RequestBody String body)
            throws IOException {
        if (!ALLOWED_FILES.contains(fileName)) {
            return ResponseEntity.notFound().build();
        }
        Files.createDirectories(BASE_DIR);
        Path file = BASE_DIR.resolve(fileName).normalize();
        if (!file.startsWith(BASE_DIR)) {
            return ResponseEntity.badRequest().build();
        }
        Files.writeString(file, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return ResponseEntity.ok().build();
    }

    // optional POST fallback for clients that don't use PUT
    @PostMapping("/{fileName:.+}")
    public ResponseEntity<Void> postMockFile(@PathVariable String fileName, @RequestBody String body)
            throws IOException {
        return putMockFile(fileName, body);
    }
}