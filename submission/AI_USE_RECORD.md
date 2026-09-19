# AI Use Record

The tool used was GitHub Copilot/Codex in VS Code. AI assistance helped structure and explain the Java scheduler, including the workload loader, FCFS and Priority policies, generator/scheduler/worker pipeline, semaphore resource control, statistics, monitor output, logging, and interruption recovery.

The implementation was developed incrementally and tested from the local PowerShell terminal. Human checks included reviewing the event flow, simplifying the logs, verifying the five-argument command format, checking workload paths, comparing Printer permit runs, and confirming that `InterruptTest` and normal single-job shutdown completed successfully.

One concrete correction was made during review: an early analysis compared one worker with five workers, although the required question compared three workers with five. The analysis was corrected to use E2 versus E4 and to include the change in resource wait (256ms to 721ms). The event logs were also checked manually to identify the different Worker assignments between E2 and E7.

The experiment table and raw logs were generated from actual local runs. The final source should be reviewed by the student before submission, and generated `.class` files should be removed from the final submission package.
