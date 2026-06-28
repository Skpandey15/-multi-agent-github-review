package com.multiagent.review.temporal.activity;

import com.multiagent.review.agent.model.CodeFile;
import com.multiagent.review.agent.model.CodeMap;
import com.multiagent.review.service.GitHubService;
import com.multiagent.review.temporal.workflow.CodeReviewWorkflow;
import io.temporal.spring.boot.ActivityImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ActivityImpl(taskQueues = CodeReviewWorkflow.TASK_QUEUE)
public class GitHubActivitiesImpl implements GitHubActivities {

    private final GitHubService gitHubService;

    @Override
    public CodeMap cloneAndMapRepository(String repoUrl, String branch) {
        log.info("Cloning repository repoUrl={} branch={}", repoUrl, branch);
        return gitHubService.cloneAndMap(repoUrl, branch);
    }

    @Override
    public String createFixBranch(String repoUrl, String baseBranch, String fixBranchName) {
        log.info("Creating fix branch={} from base={}", fixBranchName, baseBranch);
        return gitHubService.createBranch(repoUrl, baseBranch, fixBranchName);
    }

    @Override
    public void commitFileChange(String repoUrl, String branch, String filePath,
                                 String newContent, String commitMessage) {
        log.info("Committing fix to file={} branch={}", filePath, branch);
        gitHubService.commitFile(repoUrl, branch, filePath, newContent, commitMessage);
    }

    @Override
    public String raisePullRequest(String repoUrl, String fixBranch, String baseBranch,
                                   String title, String body) {
        log.info("Raising PR from branch={} to base={}", fixBranch, baseBranch);
        return gitHubService.createPullRequest(repoUrl, fixBranch, baseBranch, title, body);
    }

    @Override
    public String getFileContent(String repoUrl, String branch, String filePath) {
        return gitHubService.getFileContent(repoUrl, branch, filePath);
    }
}
