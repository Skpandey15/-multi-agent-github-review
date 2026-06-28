SELECT id,
       status,
       total_issues_found,
       total_issues_fixed,
       total_false_positives,
       pr_url,
       ROUND(EXTRACT(EPOCH FROM (completed_at - started_at))) AS duration_secs,
       started_at
FROM pipeline_runs
ORDER BY started_at DESC;

SELECT file_path,
       line_number,
       severity,
       issue_type,
       description,
       commit_message
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
  AND status = 'FIXED'
ORDER BY severity, file_path;

SELECT severity,
       status,
       COUNT(*) AS total
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
GROUP BY severity, status
ORDER BY
    CASE severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,
    status;

SELECT file_path,
       line_number,
       severity,
       description,
       critic_reasoning
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
  AND status = 'FALSE_POSITIVE'
ORDER BY severity DESC;

SELECT file_path,
       line_number,
       severity,
       issue_type,
       description,
       critic_reasoning
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
  AND status = 'NEEDS_HUMAN';

SELECT file_path,
       line_number,
       severity,
       issue_type,
       description,
       critic_reasoning
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
  AND status = 'NEEDS_HUMAN';

SELECT file_path,
       line_number,
       description,
       original_code,
       fixed_code,
       commit_message
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
  AND status = 'FIXED'
  AND original_code IS NOT NULL
ORDER BY file_path;

SELECT file_path,
       line_number,
       severity,
       status,
       description,
       commit_message
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
  AND issue_type = 'SECURITY'
ORDER BY
    CASE severity WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END;

SELECT r.id,
       r.status,
       r.total_issues_found,
       r.total_issues_fixed,
       r.total_false_positives,
       COUNT(f.id) FILTER (WHERE f.status = 'CONFIRMED')      AS confirmed,
    COUNT(f.id) FILTER (WHERE f.status = 'NEEDS_HUMAN')    AS needs_human,
    COUNT(f.id) FILTER (WHERE f.issue_type = 'SECURITY')   AS security_issues,
    r.pr_url,
       ROUND(EXTRACT(EPOCH FROM (r.completed_at - r.started_at))) AS secs
FROM pipeline_runs r
         LEFT JOIN findings f ON f.pipeline_run_id = r.id
GROUP BY r.id
ORDER BY r.started_at DESC;

SELECT file_path,
       line_number,
       severity,
       status,
       description,
       commit_message
FROM findings
WHERE pipeline_run_id = 'a1de0398-c514-4571-9841-46840624984b'
  AND status = 'CONFIRMED'
ORDER BY severity DESC;