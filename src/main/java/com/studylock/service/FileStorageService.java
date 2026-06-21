package com.studylock.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private static final long MAX_SIZE = 50 * 1024 * 1024; // 50MB

    /**
     * FIX: Store file with UUID prefix BUT preserve original filename in the URL path
     * so the browser downloads it with the correct name.
     * e.g. /uploads/lectures/abc123__Lecture1.pdf
     */
    public String store(MultipartFile file, String subFolder) throws IOException {
        if (file == null || file.isEmpty()) throw new RuntimeException("File is empty");
        if (file.getSize() > MAX_SIZE) throw new RuntimeException("File too large (max 50MB)");

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) originalName = "file";
        // Sanitize filename - remove dangerous characters
        originalName = originalName.replaceAll("[^a-zA-Z0-9._\\-() ]", "_").trim();

        String ext = originalName.contains(".")
            ? originalName.substring(originalName.lastIndexOf(".")).toLowerCase() : "";

        if (!ext.matches("\\.(pdf|doc|docx|ppt|pptx|zip|jpg|jpeg|png|webp|txt|mp4|mp3)")) {
            throw new RuntimeException("File type not allowed: " + ext);
        }

        // FIX: keep original name visible in the filename (UUID__ prefix to ensure uniqueness)
        String safeOriginal = originalName.replaceAll("\\s+", "_");
        String filename = UUID.randomUUID().toString().substring(0, 8) + "__" + safeOriginal;

        Path dir = Paths.get(uploadDir, subFolder);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return "/uploads/" + subFolder + "/" + filename;
    }

    public void delete(String fileUrl) {
        try {
            if (fileUrl != null && fileUrl.startsWith("/uploads/")) {
                Path p = Paths.get(uploadDir + fileUrl.substring("/uploads".length()));
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {}
    }

    /**
     * Returns the display name (original filename) from a stored URL.
     */
    public static String getDisplayName(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return "file";
        String name = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        // Strip UUID prefix (8chars + __)
        if (name.length() > 10 && name.charAt(8) == '_' && name.charAt(9) == '_') {
            name = name.substring(10);
        }
        return name.replaceAll("_", " ");
    }
}
