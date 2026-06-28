package com.multiagent.review.agent;

import com.multiagent.review.agent.model.CriticVerdict;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
public interface CriticAgent {

    @SystemMessage("""
            You are a sceptical senior engineer whose sole job is to CHALLENGE code review findings
            and eliminate false positives before any automated fixes are applied.

            You will receive a single finding from the Analyser agent along with the full file context.
            Your task is to independently determine whether the finding is:

            - CONFIRMED: The issue is real. The code has this problem in this context. A fix should be applied.
            - FALSE_POSITIVE: The issue is NOT real in this context. The Analyser was wrong or lacked context.
            - NEEDS_HUMAN: The issue may be real but is too complex, ambiguous, or risky to auto-fix.

            Reasoning guidelines:
            - Consider whether the flagged code is actually reachable / exploitable in context
            - Check whether surrounding code already mitigates the issue
            - Consider whether this is test/generated/deprecated code where the normal rules do not apply
            - If a SQL query uses parameterised placeholders downstream, it is NOT an injection vulnerability
            - Be especially strict on HIGH severity findings — a wrong auto-fix on production code is worse than missing an issue

            Return a single JSON object:
            {
              "filePath": "string",
              "lineNumber": number,
              "verdict": "CONFIRMED | FALSE_POSITIVE | NEEDS_HUMAN",
              "reasoning": "string (clear explanation of your decision)"
            }
            """)
    @UserMessage("""
            FINDING TO VERIFY:
            File: {{filePath}}
            Line: {{lineNumber}}
            Issue type: {{issueType}}
            Severity: {{severity}}
            Confidence: {{confidence}}%
            Description: {{description}}
            Code snippet: {{codeSnippet}}

            FULL FILE CONTEXT:
            ```{{language}}
            {{fullCode}}
            ```

            Evaluate this finding and return your CriticVerdict JSON.
            """)
    CriticVerdict verify(
            @V("filePath") String filePath,
            @V("lineNumber") int lineNumber,
            @V("issueType") String issueType,
            @V("severity") String severity,
            @V("confidence") int confidence,
            @V("description") String description,
            @V("codeSnippet") String codeSnippet,
            @V("language") String language,
            @V("fullCode") String fullCode
    );
}
