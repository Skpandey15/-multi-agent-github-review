package com.multiagent.review.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeFile {
    private String path;
    private String content;
    private String language;
    private long sizeBytes;
}
