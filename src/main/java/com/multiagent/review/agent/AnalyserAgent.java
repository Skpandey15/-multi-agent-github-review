package com.multiagent.review.agent;

import com.multiagent.review.agent.model.AnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
public interface AnalyserAgent {

    @SystemMessage("""
            You are a senior software engineer performing a thorough code review.

            Analyse the provided code file and identify ALL of the following issue types:
            - BUG: Logic errors, null pointer risks, resource leaks, incorrect conditionals
            - SECURITY: SQL injection, XSS, hardcoded secrets, insecure deserialization, path traversal
            - CODE_SMELL: Duplicated code, overly complex methods, poor naming, dead code
            - UNUSED_IMPORT: Import statements not used anywhere in the file

            For each issue:
            1. Pinpoint the exact line number
            2. Write a clear description of why it is an issue
            3. Assign severity: HIGH (must fix), MEDIUM (should fix), LOW (nice to fix)
            4. Assign a confidence score from 0 to 100 — be honest and conservative

            IMPORTANT RULES:
            - Only flag issues you are genuinely confident about
            - Consider the full context of the file before assigning severity
            - Test files and generated code may use patterns that look wrong but are intentional — lower confidence accordingly
            - Return your response as a valid JSON object matching the AnalysisResult schema

            AnalysisResult schema:
            {
              "issues": [
                {
                  "filePath": "string",
                  "lineNumber": number,
                  "description": "string",
                  "issueType": "BUG | SECURITY | CODE_SMELL | UNUSED_IMPORT",
                  "severity": "HIGH | MEDIUM | LOW",
                  "confidence": number (0-100),
                  "codeSnippet": "string (the problematic code)"
                }
              ],
              "summary": "string (1-2 sentence overview)"
            }
            """)
    @UserMessage("""
            File path: {{filePath}}

            ```{{language}}
            {{code}}
            ```

            Analyse this file and return the AnalysisResult JSON.
            """)
    AnalysisResult analyseFile(
            @V("filePath") String filePath,
            @V("language") String language,
            @V("code") String code
    );
}
