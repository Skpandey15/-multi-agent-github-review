package com.multiagent.review.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeMap {
    private String repoUrl;
    private String branch;
    private String detectedLanguage;
    private String detectedFramework;
    private List<CodeFile> files;
    private int totalFiles;
}
