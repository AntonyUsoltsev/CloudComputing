import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * Утилита для генерации JSON запроса задачи из скомпилированного класса.
 */
public class GenerateTaskRequest {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java GenerateTaskRequest <class-file> <class-name> <method-name> [arg1] [arg2] ...");
            System.out.println("Example: java GenerateTaskRequest SimpleCalculator.class SimpleCalculator sum 32 44");
            return;
        }

        String classFile = args[0];
        String className = args[1];
        String methodName = args[2];

        byte[] classBytes = Files.readAllBytes(Paths.get(classFile));
        String classBytesBase64 = Base64.getEncoder().encodeToString(classBytes);

        Object[] arguments = new Object[args.length - 3];
        for (int i = 3; i < args.length; i++) {
            // Пытаемся распарсить как int, если не получается - оставляем как String
            try {
                arguments[i - 3] = Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
                arguments[i - 3] = args[i];
            }
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(arguments);
        oos.close();
        String argumentsBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        
        String taskId = UUID.randomUUID().toString();
        String json = String.format(
            "{\n" +
            "  \"taskId\": \"%s\",\n" +
            "  \"className\": \"%s\",\n" +
            "  \"methodName\": \"%s\",\n" +
            "  \"classBytes\": \"%s\",\n" +
            "  \"arguments\": \"%s\",\n" +
            "  \"codeHash\": \"%s-v1\",\n" +
            "  \"metadata\": {\n" +
            "    \"createdAt\": \"%s\",\n" +
            "    \"priority\": 1,\n" +
            "    \"timeoutMs\": 5000\n" +
            "  }\n" +
            "}",
            taskId,
            className,
            methodName,
            classBytesBase64,
            argumentsBase64,
            className.toLowerCase(),
            java.time.Instant.now().toString()
        );
        
        System.out.println(json);
    }
}

