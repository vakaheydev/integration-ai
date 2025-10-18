package ai.agent.swagger.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

interface Assistant {
    @SystemMessage("Ты - личный секретарь-помощник по ведению дел")
    @UserMessage("{{prompt}}")
    Flux<String> chat(@V("prompt") String prompt);
}

class Tools {
    @Tool("Функция для получения текущего времени")
    String currentTime() {
        System.out.println("Запрос для получения времени...");
        return LocalTime.now().toString();
    }

    @Tool("Функция для получения текущей даты и текущего времени")
    String currentDateTime() {
        System.out.println("Запрос для получения даты и времени...");
        return LocalDateTime.now().toString();
    }

    @Tool("Функция для получения текущей даты")
    String currentDate() {
        System.out.println("Запрос для получения даты...");
        return LocalDate.now().toString();
    }

    @Tool("Функция для чтения файла по его названию. Функция возвращает содержимое файла в виде строки.")
    String readFile(@P("Название файла") String filename) {
        try {
            System.out.println("Читаю файл " + filename);
            return Files.readString(
                    Path.of("C:\\Users\\Vaka\\Documents\\диплом\\src\\ai-agent-analyze\\src\\main\\resources\\content" + filename));
        } catch (IOException e) {
            return "Не удалось прочитать файл";
        }
    }

    @Tool("Функция для получения массива названий всех доступных файлов.")
    String[] getFiles() {
        try {
            System.out.println("Получаю список всех файлов");
            File dir = new File(
                    "C:\\Users\\Vaka\\Documents\\диплом\\src\\ai-agent-analyze\\src\\main\\resources\\content");
            return dir.list();
        } catch (Exception e) {
            return new String[]{"Не удалось получить массив названий всех доступных файлов"};
        }
    }

    @Tool("Функция для создания файла с расширением .txt с заданным названием и заданным содержимым (строкой). Если пользователь указывает расширение - оставить только название файла, без расширения")
    String createTxtFileWithContent(@P("Название файла без расширения") String filename,
                                    @P("Содержимое файла") String content) {
        try {
            System.out.println("Создаю файл " + filename);
            Files.writeString(Path.of("C:\\Users\\Vaka\\Documents\\диплом\\experiments\\" + filename + ".txt"),
                    content);
            return "Файл создан успешно";
        } catch (IOException e) {
            return "Ошибка при попытке создания файла";
        }
    }
}

@Service
public class GeminiClient {
    @Value("${secrets.gemini.api-key}")
    private String apikey;

    private final ChatMemory memory = MessageWindowChatMemory.withMaxMessages(30);

    public String sendPrompt(String prompt) {

        StreamingChatModel geminiModel = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(apikey)
                .modelName("gemini-2.0-flash")
                .build();

        Assistant gemini = AiServices.builder(Assistant.class)
                .streamingChatModel(geminiModel)
                .chatMemory(memory)
                .tools(new Tools())
                .build();

        Flux<String> response = gemini.chat(prompt);
        printResponse(response);
        return null;
    }

    private void printResponse(Flux<String> response) {
        response.doOnNext(System.out::print) // Выводим каждую часть по мере прихода
                .doOnComplete(() -> System.out.println("\n--- Reponse End"))
                .doOnError(error -> System.err.println("Произошла ошибка: " + error.getMessage()))
                .blockLast();
    }
}

