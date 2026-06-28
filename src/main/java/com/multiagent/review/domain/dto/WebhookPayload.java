package com.multiagent.review.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookPayload {

    @JsonProperty("ref")
    private String ref;

    @JsonProperty("repository")
    private Repository repository;

    @JsonProperty("head_commit")
    private HeadCommit headCommit;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        @JsonProperty("clone_url")
        private String cloneUrl;

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("default_branch")
        private String defaultBranch;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HeadCommit {
        @JsonProperty("id")
        private String id;

        @JsonProperty("message")
        private String message;

        @JsonProperty("author")
        private Author author;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;
    }

    public String getBranchName() {
        if (ref == null) return null;
        return ref.replace("refs/heads/", "");
    }
}
