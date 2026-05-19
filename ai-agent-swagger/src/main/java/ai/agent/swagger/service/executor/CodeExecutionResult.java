package ai.agent.swagger.service.executor;

import lombok.Getter;

@Getter
public class CodeExecutionResult {

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;

    public CodeExecutionResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.timedOut = timedOut;
    }

    public static CodeExecutionResult timeout() {
        return new CodeExecutionResult(-1, "", "Execution timed out", true);
    }

    public boolean isSuccess() {
        return !timedOut && exitCode == 0;
    }

    /** Удобный вывод для логов и ответа AI */
    public String toSummary() {
        if (timedOut) return "TIMEOUT";
        if (isSuccess()) return "SUCCESS\nOUTPUT:\n" + stdout;
        return "FAILED (exit=" + exitCode + ")\nSTDOUT:\n" + stdout + "\nSTDERR:\n" + stderr;
    }
}

