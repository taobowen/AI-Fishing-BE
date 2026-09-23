-- UNSCORABLE is an additive eval status for counterfactual action/target mismatch.
-- It is not success or failure; historical outcome cannot be attributed.
-- Numbered V46 so V45 can own agent_runtime_control (Task C).

ALTER TABLE guidance_eval_case_results
    DROP CONSTRAINT guidance_eval_case_results_status_check;

ALTER TABLE guidance_eval_case_results
    ADD CONSTRAINT guidance_eval_case_results_status_check CHECK (
        status IN ('PASS', 'FAIL', 'ERROR', 'SKIP', 'UNSCORABLE')
    );
