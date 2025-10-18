package ai.agent.swagger;

import ai.agent.swagger.service.GeminiClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Scanner;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(App.class, args);
        GeminiClient gemini = ctx.getBean(GeminiClient.class);
        while (true) {
            System.out.print("Ваш запрос: ");
            Scanner scanner = new Scanner(System.in);
            gemini.sendPrompt(scanner.nextLine());
        }
    }
}
