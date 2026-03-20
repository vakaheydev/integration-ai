package ai.agent.swagger.service.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class PythonCodeExecutorService {

    private final PythonExecutorProperties props;
    private DockerClient dockerClient;

    public PythonCodeExecutorService(PythonExecutorProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        var configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (props.getDockerHost() != null) {
            configBuilder.withDockerHost(props.getDockerHost());
        }
        var config = configBuilder.build();

        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(10)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(props.getTimeoutSeconds() + 10))
                .build();

        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        log.info("PythonCodeExecutorService initialized. Docker host: {}",
                props.getDockerHost() != null ? props.getDockerHost() : "system default");

        try {
            if (props.getDockerfilePath() != null) {
                buildImageIfNeeded(props.getImage(), props.getDockerfilePath());
            } else {
                pullImage(props.getImage());
            }
        } catch (Exception e) {
            log.warn("Could not prepare image '{}' at startup: {}. Will retry at execution time.", props.getImage(), e.getMessage());
        }
    }

    private void buildImageIfNeeded(String imageName, String dockerfilePath) throws InterruptedException {
        // Проверяем наличие образа
        boolean exists = !dockerClient.listImagesCmd()
                .withImageNameFilter(imageName)
                .exec()
                .isEmpty();

        if (exists) {
            log.info("Custom image '{}' already exists locally, skipping build", imageName);
            return;
        }

        java.io.File dockerfileDir = new java.io.File(dockerfilePath);
        if (!dockerfileDir.exists()) {
            log.warn("Dockerfile path '{}' does not exist, falling back to pull", dockerfilePath);
            pullImage(imageName);
            return;
        }

        log.info("Building custom Docker image '{}' from '{}'...", imageName, dockerfilePath);
        dockerClient.buildImageCmd()
                .withBaseDirectory(dockerfileDir)
                .withDockerfile(new java.io.File(dockerfileDir, "Dockerfile"))
                .withTags(java.util.Set.of(imageName))
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(com.github.dockerjava.api.model.BuildResponseItem item) {
                        if (item.getStream() != null) {
                            String line = item.getStream().strip();
                            if (!line.isBlank()) log.debug("Build: {}", line);
                        }
                    }
                })
                .awaitCompletion();
        log.info("Custom image '{}' built successfully", imageName);
    }

    private void pullImage(String image) throws InterruptedException {
        log.info("Pulling Docker image '{}'...", image);
        dockerClient.pullImageCmd(image)
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(com.github.dockerjava.api.model.PullResponseItem item) {
                        if (item.getStatus() != null) {
                            log.debug("Pull [{}]: {} {}", image, item.getStatus(),
                                    item.getProgressDetail() != null ? item.getProgressDetail() : "");
                        }
                    }
                })
                .awaitCompletion();
        log.info("Docker image '{}' ready", image);
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (dockerClient != null) {
            dockerClient.close();
        }
    }

    /**
     * Выполняет Python-код в изолированном Docker-контейнере.
     *
     * @param code Python-код для выполнения
     * @return результат выполнения
     */
    public CodeExecutionResult execute(String code) {
        String containerId = null;

        try {
            // 1. Убеждаемся что образ есть
            try {
                if (props.getDockerfilePath() != null) {
                    buildImageIfNeeded(props.getImage(), props.getDockerfilePath());
                } else {
                    pullImage(props.getImage());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new CodeExecutionResult(-1, "", "Interrupted while preparing image", false);
            } catch (Exception e) {
                log.warn("Image preparation failed, proceeding anyway: {}", e.getMessage());
            }

            // 2. Конфигурируем HostConfig — без bind mount
            // --add-host позволяет обращаться к хост-машине через host.docker.internal
            long memoryBytes = parseMemory(props.getMemoryLimit());
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withMemory(memoryBytes)
                    .withNanoCPUs((long) (props.getCpuLimit() * 1_000_000_000L))
                    .withExtraHosts("host.docker.internal:host-gateway")
                    .withAutoRemove(false);

            // 3. Заменяем localhost → host.docker.internal чтобы запросы шли на хост, а не внутрь контейнера
            String preparedCode = rewriteLocalhostToDockerHost(code);

            // 4. Создаём контейнер — запускаем через sh: сначала pip install, потом скрипт
            String shellCmd = buildShellCommand(preparedCode);
            CreateContainerResponse container = dockerClient
                    .createContainerCmd(props.getImage())
                    .withHostConfig(hostConfig)
                    .withCmd("sh", "-c", shellCmd)
                    .withWorkingDir("/code")
                    .exec();

            containerId = container.getId();
            log.debug("Container created: {}", containerId);

            // 5. Копируем скрипт внутрь контейнера через tar-архив
            byte[] tarBytes = createTar("script.py", preparedCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (InputStream tarStream = new ByteArrayInputStream(tarBytes)) {
                dockerClient.copyArchiveToContainerCmd(containerId)
                        .withTarInputStream(tarStream)
                        .withRemotePath("/code")
                        .exec();
            }
            log.debug("Script copied into container {}", containerId);

            // 5. Запускаем
            dockerClient.startContainerCmd(containerId).exec();

            // 6. Собираем stdout + stderr через attach
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);

            dockerClient.attachContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            String line = new String(frame.getPayload());
                            switch (frame.getStreamType()) {
                                case STDOUT -> stdout.append(line);
                                case STDERR -> stderr.append(line);
                                default -> {}
                            }
                        }

                        @Override
                        public void onComplete() {
                            latch.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("Error reading container output", throwable);
                            latch.countDown();
                        }
                    });

            // 7. Ждём завершения с таймаутом
            boolean finished = latch.await(props.getTimeoutSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                log.warn("Container {} timed out, killing", containerId);
                dockerClient.killContainerCmd(containerId).exec();
                return CodeExecutionResult.timeout();
            }

            // 8. Получаем exit code
            AtomicInteger exitCode = new AtomicInteger(-1);
            dockerClient.waitContainerCmd(containerId)
                    .exec(new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.WaitResponse response) {
                            exitCode.set(response.getStatusCode());
                        }
                    })
                    .awaitCompletion(5, TimeUnit.SECONDS);

            log.debug("Container {} finished with exit code {}", containerId, exitCode.get());
            return new CodeExecutionResult(exitCode.get(), stdout.toString(), stderr.toString(), false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CodeExecutionResult(-1, "", "Execution interrupted", false);
        } catch (Exception e) {
            log.error("Failed to execute Python code in Docker", e);
            return new CodeExecutionResult(-1, "", "Docker execution error: " + e.getMessage(), false);
        } finally {
            // 9. Удаляем контейнер
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                    log.debug("Container {} removed", containerId);
                } catch (Exception e) {
                    log.warn("Failed to remove container {}: {}", containerId, e.getMessage());
                }
            }
        }
    }

    /**
     * Создаёт tar-архив в памяти с одним файлом.
     * Docker API принимает файлы именно в tar-формате.
     */
    private byte[] createTar(String filename, byte[] content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var tarOut = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(baos)) {
            var entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(filename);
            entry.setSize(content.length);
            tarOut.putArchiveEntry(entry);
            tarOut.write(content);
            tarOut.closeArchiveEntry();
        }
        return baos.toByteArray();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Строит shell-команду: pip install нужных пакетов + запуск скрипта.
     * Пакеты определяются по import-строкам в коде.
     * Стандартные библиотеки Python пропускаются.
     */
    private String buildShellCommand(String code) {
        java.util.Set<String> packages = extractThirdPartyPackages(code);
        boolean usePytest = isPytestCode(code);

        // pytest нужен как зависимость если используется
        if (usePytest) {
            packages.add("pytest");
        }

        String runCmd = usePytest
                ? "pytest /code/script.py -v --tb=short"
                : "python /code/script.py";

        if (packages.isEmpty()) {
            return runCmd;
        }
        String pipInstall = "pip install --quiet --root-user-action=ignore --disable-pip-version-check " + String.join(" ", packages);
        log.debug("Auto pip install: {}", pipInstall);
        return pipInstall + " && " + runCmd;
    }

    /**
     * Определяет, является ли код pytest-тестами.
     * Проверяет наличие import pytest или функций test_*.
     */
    private boolean isPytestCode(String code) {
        return code.contains("import pytest")
                || code.contains("from pytest")
                || java.util.regex.Pattern.compile("^def\\s+test_", java.util.regex.Pattern.MULTILINE).matcher(code).find();
    }

    private static final java.util.Set<String> STDLIB_MODULES = java.util.Set.of(
        "os", "sys", "re", "io", "abc", "ast", "csv", "copy", "enum", "json",
        "math", "time", "uuid", "zlib", "gzip", "glob", "html", "http", "hmac",
        "hashlib", "logging", "pathlib", "pickle", "queue", "random", "shutil",
        "signal", "socket", "string", "struct", "typing", "base64", "bisect",
        "builtins", "calendar", "cmath", "codecs", "collections", "concurrent",
        "contextlib", "dataclasses", "datetime", "decimal", "difflib", "email",
        "fileinput", "fnmatch", "fractions", "functools", "gc", "getpass",
        "getopt", "graphlib", "heapq", "inspect", "itertools", "keyword",
        "linecache", "locale", "mimetypes", "multiprocessing", "numbers",
        "operator", "optparse", "pdb", "platform", "pprint", "profile",
        "pstats", "pty", "pwd", "readline", "resource", "runpy", "secrets",
        "select", "shelve", "shlex", "site", "smtplib", "sndhdr", "sqlite3",
        "sre_compile", "sre_parse", "ssl", "stat", "statistics", "subprocess",
        "tarfile", "telnetlib", "tempfile", "textwrap", "threading", "timeit",
        "tkinter", "token", "tokenize", "tomllib", "traceback", "tracemalloc",
        "types", "unicodedata", "unittest", "urllib", "uu", "venv", "warnings",
        "weakref", "webbrowser", "wsgiref", "xml", "xmlrpc", "zipfile", "zipimport",
        "__future__", "argparse", "array", "atexit", "audioop", "binascii",
        "binhex", "bz2", "cgi", "cgitb", "chunk", "cProfile", "compileall",
        "crypt", "ctypes", "curses", "dbm", "dis", "distutils", "doctest",
        "encodings", "errno", "faulthandler", "formatter", "ftplib", "gettext",
        "imaplib", "imghdr", "importlib", "ipaddress", "lib2to3", "lzma",
        "mailbox", "mailcap", "marshal", "mmap", "modulefinder", "netrc",
        "nis", "nntplib", "ntpath", "nturl2path", "ossaudiodev", "parser",
        "pickletools", "pipes", "pkgutil", "poplib", "posix", "posixpath",
        "pyclbr", "pydoc", "quopri", "rlcompleter", "sched", "socketserver",
        "spwd", "sunau", "symtable", "sysconfig", "syslog", "tabnanny",
        "termios", "test", "tty", "turtle", "turtledemo",
        "wave", "winreg", "winsound", "xdrlib"
    );

    // Маппинг import-имени → pip-пакет (когда они отличаются)
    private static final java.util.Map<String, String> IMPORT_TO_PACKAGE = java.util.Map.ofEntries(
        java.util.Map.entry("cv2",          "opencv-python"),
        java.util.Map.entry("PIL",          "Pillow"),
        java.util.Map.entry("sklearn",      "scikit-learn"),
        java.util.Map.entry("bs4",          "beautifulsoup4"),
        java.util.Map.entry("yaml",         "pyyaml"),
        java.util.Map.entry("dotenv",       "python-dotenv"),
        java.util.Map.entry("dateutil",     "python-dateutil"),
        java.util.Map.entry("jwt",          "PyJWT"),
        java.util.Map.entry("attr",         "attrs"),
        java.util.Map.entry("google",       "google-api-python-client"),
        java.util.Map.entry("Crypto",       "pycryptodome"),
        java.util.Map.entry("serial",       "pyserial"),
        java.util.Map.entry("usb",          "pyusb"),
        java.util.Map.entry("gi",           "PyGObject"),
        java.util.Map.entry("wx",           "wxPython"),
        java.util.Map.entry("pkg_resources","setuptools")
    );

    private java.util.Set<String> extractThirdPartyPackages(String code) {
        java.util.Set<String> packages = new java.util.LinkedHashSet<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "^\\s*(?:import|from)\\s+([a-zA-Z0-9_]+)",
            java.util.regex.Pattern.MULTILINE
        );
        java.util.regex.Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String module = matcher.group(1);
            if (!STDLIB_MODULES.contains(module)) {
                String pkg = IMPORT_TO_PACKAGE.getOrDefault(module, module);
                packages.add(pkg);
            }
        }
        return packages;
    }

    /**
     * Заменяет все вхождения localhost/127.0.0.1 на host.docker.internal,
     * чтобы запросы из контейнера шли на хост-машину.
     * Учитывает строки вида: "http://localhost:8080", 'localhost', "127.0.0.1:3000" и т.д.
     */
    private String rewriteLocalhostToDockerHost(String code) {
        return code
                .replace("localhost", "host.docker.internal")
                .replace("127.0.0.1", "host.docker.internal");
    }

    /** Парсит строку памяти вида "128m", "1g" в байты */
    private long parseMemory(String limit) {
        if (limit == null || limit.isBlank()) return 128L * 1024 * 1024;
        String lower = limit.trim().toLowerCase();
        if (lower.endsWith("g")) return Long.parseLong(lower.replace("g", "")) * 1024 * 1024 * 1024;
        if (lower.endsWith("m")) return Long.parseLong(lower.replace("m", "")) * 1024 * 1024;
        if (lower.endsWith("k")) return Long.parseLong(lower.replace("k", "")) * 1024;
        return Long.parseLong(lower);
    }
}
