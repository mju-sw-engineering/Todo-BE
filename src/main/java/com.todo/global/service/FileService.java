package com.todo.global.service;

import com.todo.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileService {

    private final Path profileUploadPath;
    private final Path teamUploadPath;

    public FileService(@Value("${file.upload-dir}") String uploadDir) throws IOException {
        this.profileUploadPath = Paths.get(uploadDir, "profiles");
        this.teamUploadPath = Paths.get(uploadDir, "teams");
        Files.createDirectories(profileUploadPath);
        Files.createDirectories(teamUploadPath);
    }

    public String saveProfileImage(MultipartFile file) {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        try {
            file.transferTo(profileUploadPath.resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("파일 저장에 실패했습니다.", e);
        }
        return "/uploads/profiles/" + filename;
    }

    public String saveTeamImage(MultipartFile file) {
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        try {
            file.transferTo(teamUploadPath.resolve(filename));
        } catch (IOException e) {
            throw new BusinessException("파일 저장에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return "/uploads/teams/" + filename;
    }
}
