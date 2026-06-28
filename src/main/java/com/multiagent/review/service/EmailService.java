package com.multiagent.review.service;

import com.multiagent.review.exception.EmailDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.to}")
    private String toEmail;

    public void sendAnalysisComplete(UUID runId, String repoUrl, int issuesFound) {
        String subject = String.format("[Code Review] Analysis complete — %d issue(s) found", issuesFound);
        String body = String.format("""
                Code review analysis has completed.

                Repository   : %s
                Run ID       : %s
                Issues Found : %d

                The Critic agent is now verifying each finding to remove false positives.
                You will receive another email when the PR is ready for review.
                """, repoUrl, runId, issuesFound);
        send(subject, body);
    }

    public void sendPrRaised(UUID runId, String repoUrl, String prUrl,
                             int issuesFixed, int issuesSkipped) {
        String subject = String.format("[Code Review] PR raised — %d fix(es) applied", issuesFixed);
        String body = String.format("""
                A pull request has been raised with automated code fixes.

                Repository      : %s
                Run ID          : %s
                PR Link         : %s
                Issues Fixed    : %d
                Issues Skipped  : %d (low confidence / needs human review)

                Please review the PR and merge or close it at your discretion.
                No code has been merged automatically.
                """, repoUrl, runId,
                prUrl != null ? prUrl : "No PR raised (no high-confidence fixes found)",
                issuesFixed, issuesSkipped);
        send(subject, body);
    }

    private void send(String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent. subject='{}'", subject);
        } catch (Exception e) {
            log.error("Failed to send email. subject='{}' error={}", subject, e.getMessage(), e);
            throw new EmailDeliveryException("Email delivery failed for subject: " + subject, e);
        }
    }
}
