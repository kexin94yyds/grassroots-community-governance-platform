package com.cunzhi.governance.attachment.service;

import com.cunzhi.governance.common.error.BusinessException;
import com.cunzhi.governance.common.error.ErrorCode;
import com.cunzhi.governance.config.AppProperties;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Shared verified storage primitive for event and task attachments. */
public final class AttachmentFileStore {

    private static final int BUFFER_SIZE = 16 * 1024;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf"
    );

    private final AppProperties.Attachment properties;

    public AttachmentFileStore(AppProperties.Attachment properties) {
        this.properties = properties;
    }

    public StoredFile store(MultipartFile file) {
        validateUpload(file);
        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String declaredContentType = normalizeContentType(file.getContentType());
        Path root = storageRoot();
        Path temporary = null;
        Path stored = null;
        try {
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            CopyResult copy = copyAndDigest(file, temporary);
            String detectedContentType = detectContentType(temporary);
            if (!detectedContentType.equals(declaredContentType)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件内容与声明的 MIME 类型不一致");
            }
            String storageKey = UUID.randomUUID().toString();
            stored = resolveStoragePath(root, storageKey);
            moveAtomically(temporary, stored);
            return new StoredFile(storageKey, originalName, detectedContentType, copy.fileSize(), copy.sha256(), stored);
        } catch (BusinessException exception) {
            deleteQuietly(temporary);
            deleteQuietly(stored);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(temporary);
            deleteQuietly(stored);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件保存失败");
        }
    }

    public Path requireExistingFile(String storageKey) {
        Path root = storageRoot();
        Path path = resolveStoragePath(root, storageKey);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
            }
            Path realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realPath.startsWith(root)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "附件存储路径无效");
            }
            return realPath;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件不存在");
        }
    }

    /**
     * Deletes a committed attachment file. Missing files are intentionally accepted so a stale
     * storage object never prevents the database lifecycle record from being retired.
     */
    public void deleteStoredFileIfExists(String storageKey) {
        Path root = storageRoot();
        Path path = resolveStoragePath(root, storageKey);
        try {
            if (Files.isSymbolicLink(path)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "附件存储路径无效");
            }
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件物理删除失败");
        }
    }

    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup is used only for a failed upload before a DB row exists.
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择非空附件");
        }
        if (properties == null || properties.maxFileSizeBytes() <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件大小限制未正确配置");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件不能超过 10 MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        List<String> configuredTypes = properties.allowedContentTypes();
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType) || configuredTypes == null || !configuredTypes.contains(contentType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持 JPEG、PNG 或 PDF 附件");
        }
    }

    private CopyResult copyAndDigest(MultipartFile file, Path target) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统不支持 SHA-256");
        }
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > properties.maxFileSizeBytes()) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件不能超过 10 MB");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (total == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择非空附件");
        }
        return new CopyResult(total, HexFormat.of().formatHex(digest.digest()));
    }

    private String detectContentType(Path path) throws IOException {
        byte[] header = new byte[8];
        int length;
        try (InputStream input = Files.newInputStream(path)) {
            length = input.read(header);
        }
        if (length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47
                && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a) {
            return "image/png";
        }
        if (length >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-') {
            return "application/pdf";
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "无法识别附件内容类型");
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator < 0 ? contentType : contentType.substring(0, separator)).trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOriginalName(String originalName) {
        String normalized = originalName == null ? "" : originalName.replace('\\', '/')
                .replaceAll("[\\p{Cntrl}]", "").trim();
        int separator = normalized.lastIndexOf('/');
        if (separator >= 0) {
            normalized = normalized.substring(separator + 1).trim();
        }
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "附件名称不能为空");
        }
        return normalized.length() <= 255 ? normalized : normalized.substring(normalized.length() - 255);
    }

    private Path storageRoot() {
        if (properties == null || properties.storageRoot() == null || properties.storageRoot().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件存储目录未配置");
        }
        try {
            Path configured = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
            Files.createDirectories(configured);
            if (!Files.isDirectory(configured, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(configured)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件存储目录无效");
            }
            return configured.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件存储目录不可用");
        }
    }

    private Path resolveStoragePath(Path root, String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}")) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件存储键无效");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "附件存储路径无效");
        }
        return resolved;
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    public record StoredFile(String storageKey, String originalName, String contentType, long fileSize, String sha256, Path path) {
    }

    private record CopyResult(long fileSize, String sha256) {
    }
}
