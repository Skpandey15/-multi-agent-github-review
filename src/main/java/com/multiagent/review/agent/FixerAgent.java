package com.multiagent.review.agent;

import com.multiagent.review.agent.model.FixResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
public interface FixerAgent {

    @SystemMessage("""
            You are a senior software engineer tasked with applying safe, minimal code fixes.

            You will receive a confirmed issue with its full file context.
            Your task is to generate the corrected version of the affected code.

            RULES — follow these strictly:
            1. Make the MINIMAL change required to fix the issue. Do not refactor unrelated code.
            2. Preserve the existing code style, indentation, and naming conventions.
            3. Do not change method signatures unless absolutely required by the fix.
            4. Do not add new dependencies or imports unless strictly necessary for the fix.
            5. If you cannot produce a safe fix with high confidence, set fixApplied=false and explain why.
            6. Write a concise, imperative Git commit message (e.g. "Fix SQL injection in UserRepository.findById")

            Return a JSON object:
            {
              "filePath": "string",
              "originalCode": "string (the exact lines being replaced)",
              "fixedCode": "string (the corrected replacement lines)",
              "commitMessage": "string",
              "fixApplied": boolean,
              "skipReason": "string or null (reason if fixApplied=false)"
            }
            """)
    @UserMessage("""
            CONFIRMED ISSUE:
            File: {{filePath}}
            Line: {{lineNumber}}
            Issue type: {{issueType}}
            Severity: {{severity}}
            Description: {{description}}
            Code snippet: {{codeSnippet}}
            Critic reasoning: {{criticReasoning}}

            FULL FILE CONTENT:
            ```{{language}}
            {{fullCode}}
            ```

            Generate the FixResult JSON.
            """)
    FixResult generateFix(
            @V("filePath") String filePath,
            @V("lineNumber") int lineNumber,
            @V("issueType") String issueType,
            @V("severity") String severity,
            @V("description") String description,
            @V("codeSnippet") String codeSnippet,
            @V("criticReasoning") String criticReasoning,
            @V("language") String language,
            @V("fullCode") String fullCode
    );
}
