package com.multiagent.review.temporal.activity;

import com.multiagent.review.agent.model.CodeMap;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

@ActivityInterface
public interface GitHubActivities {

    @ActivityMethod
    CodeMap cloneAndMapRepository(String repoUrl, String branch);

    @ActivityMethod
    String createFixBranch(String repoUrl, String baseBranch, String fixBranchName);

    @ActivityMethod
    void commitFileChange(String repoUrl, String branch, String filePath,
                          String newContent, String commitMessage);

    @ActivityMethod
    String raisePullRequest(String repoUrl, String fixBranch, String baseBranch,
                            String title, String body);

    @ActivityMethod
    String getFileContent(String repoUrl, String branch, String filePath);
}
