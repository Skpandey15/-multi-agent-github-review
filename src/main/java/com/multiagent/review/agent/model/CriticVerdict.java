package com.multiagent.review.agent.model;

import com.multiagent.review.domain.enums.FindingStatus;
import lombok.Data;

@Data
public class CriticVerdict {
    private String filePath;
    private int lineNumber;
    private FindingStatus verdict;
    private String reasoning;
}
