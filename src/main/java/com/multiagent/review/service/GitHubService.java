package com.multiagent.review.service;

import com.multiagent.review.agent.model.CodeFile;
import com.multiagent.review.agent.model.CodeMap;
import com.multiagent.review.exception.GitHubApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".go", ".rb", ".cs", ".cpp", ".c", ".kt"
    );

    private final GitHub gitHub;

    public CodeMap cloneAndMap(String repoUrl, String branch) {
        try {
            String repoName = extractRepoName(repoUrl);
            GHRepository repo = gitHub.getRepository(repoName);
            GHTree tree = repo.getTreeRecursive(branch, 1);

            List<CodeFile> files = new ArrayList<>();
            String detectedLanguage = "unknown";

            for (GHTreeEntry entry : tree.getTree()) {
                if (!"blob".equals(entry.getType())) continue;
                if (!hasSupportedExtension(entry.getPath())) continue;
                if (entry.getSize() > 50_000) continue;

                String content = readFileContent(repo, branch, entry.getPath());
                String language = detectLanguage(entry.getPath());

                if (detectedLanguage.equals("unknown")) {
                    detectedLanguage = language;
                }

                files.add(CodeFile.builder()
                        .path(entry.getPath())
                        .content(content)
                        .language(language)
                        .sizeBytes(entry.getSize())
                        .build());
            }

            return CodeMap.builder()
                    .repoUrl(repoUrl)
                    .branch(branch)
                    .detectedLanguage(detectedLanguage)
                    .detectedFramework(detectFramework(files))
                    .files(files)
                    .totalFiles(files.size())
                    .build();

        } catch (IOException e) {
            throw new GitHubApiException("Failed to clone and map repository: " + repoUrl, e);
        }
    }

    public String createBranch(String repoUrl, String baseBranch, String newBranchName) {
        try {
            String repoName = extractRepoName(repoUrl);
            GHRepository repo = gitHub.getRepository(repoName);
            GHRef baseRef = repo.getRef("heads/" + baseBranch);
            repo.createRef("refs/heads/" + newBranchName, baseRef.getObject().getSha());
            return newBranchName;
        } catch (IOException e) {
            throw new GitHubApiException("Failed to create branch: " + newBranchName, e);
        }
    }

    public void commitFile(String repoUrl, String branch, String filePath,
                           String newContent, String commitMessage) {
        try {
            String repoName = extractRepoName(repoUrl);
            GHRepository repo = gitHub.getRepository(repoName);
            GHContent existingFile = repo.getFileContent(filePath, branch);
            existingFile.update(newContent, commitMessage, branch);
        } catch (IOException e) {
            throw new GitHubApiException("Failed to commit file: " + filePath, e);
        }
    }

    public String createPullRequest(String repoUrl, String fixBranch, String baseBranch,
                                    String title, String body) {
        try {
            String repoName = extractRepoName(repoUrl);
            GHRepository repo = gitHub.getRepository(repoName);
            GHPullRequest pr = repo.createPullRequest(title, fixBranch, baseBranch, body);
            pr.addLabels("auto-review", "ai-generated");
            return pr.getHtmlUrl().toString();
        } catch (IOException e) {
            throw new GitHubApiException("Failed to create pull request", e);
        }
    }

    public String getFileContent(String repoUrl, String branch, String filePath) {
        try {
            String repoName = extractRepoName(repoUrl);
            GHRepository repo = gitHub.getRepository(repoName);
            return readFileContent(repo, branch, filePath);
        } catch (IOException e) {
            throw new GitHubApiException("Failed to read file: " + filePath, e);
        }
    }

    private String readFileContent(GHRepository repo, String branch, String path) throws IOException {
        GHContent content = repo.getFileContent(path, branch);
        byte[] decoded = Base64.getMimeDecoder().decode(content.getEncodedContent());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String extractRepoName(String repoUrl) {
        return repoUrl.replace("https://github.com/", "").replaceAll("\\.git$", "");
    }

    private boolean hasSupportedExtension(String path) {
        return SUPPORTED_EXTENSIONS.stream().anyMatch(path::endsWith);
    }

    private String detectLanguage(String path) {
        if (path.endsWith(".java")) return "java";
        if (path.endsWith(".py")) return "python";
        if (path.endsWith(".js")) return "javascript";
        if (path.endsWith(".ts")) return "typescript";
        if (path.endsWith(".go")) return "go";
        if (path.endsWith(".rb")) return "ruby";
        if (path.endsWith(".cs")) return "csharp";
        if (path.endsWith(".kt")) return "kotlin";
        return "text";
    }

    private String detectFramework(List<CodeFile> files) {
        boolean hasSpring = files.stream().anyMatch(f ->
                f.getContent().contains("@SpringBootApplication") ||
                f.getContent().contains("@RestController"));
        if (hasSpring) return "Spring Boot";
        boolean hasDjango = files.stream().anyMatch(f -> f.getContent().contains("from django"));
        if (hasDjango) return "Django";
        return "unknown";
    }
}
