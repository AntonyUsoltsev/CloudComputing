# Cloud Computing - Распределённые вычисления

Система для выполнения вычислений на удалённых узлах с передачей кода и данных.

## Архитектура

- **Dispatcher** - центральный сервис для управления задачами и балансировки нагрузки
- **Worker** - исполнительный узел для выполнения задач
- **Client API** - библиотека для взаимодействия с системой
- **Model** - общие модели данных
- **Common** - общие утилиты

## Структура проекта

```
CloudComputing/
├── dispatcher/          # Dispatcher сервис
│   ├── DispatcherServer.java  # Центральный сервер управления задачами
│   └── Main.java              # Точка входа
├── worker/              # Worker сервис
│   ├── WorkerServer.java      # Сервер worker-узла
│   ├── TaskExecutor.java      # Исполнитель задач
│   ├── DynamicClassLoader.java # Динамическая загрузка классов
│   └── DispatcherClient.java  # Клиент для связи с dispatcher
├── client-api/          # Клиентская библиотека
│   ├── Cloud.java            # Фабрика для создания клиентов
│   ├── CloudClient.java      # Клиент для работы с dispatcher
│   ├── RemoteFunction.java   # Обертка для удаленных функций
│   ├── DistributedMap.java   # Распределенный parallel map
│   └── TaskBuilder.java      # Утилита для создания задач
├── model/               # Модели данных
│   ├── Task.java            # Модель задачи
│   ├── TaskResult.java     # Модель результата
│   ├── WorkerInfo.java     # Информация о worker
│   └── TaskMetadata.java   # Метаданные задачи
├── common/              # Общие утилиты
│   ├── JacksonConfig.java  # Конфигурация Jackson
│   └── HashUtils.java      # Утилиты для хеширования
└── docker-compose.yml   # Конфигурация Docker Compose
```

## Быстрый старт

### Через Docker Compose

```bash
docker-compose up --build
```

Запустит:
- Dispatcher на `http://localhost:8080`
- Worker на `http://localhost:8081`

### Локальный запуск

```bash
# Dispatcher
cd dispatcher
mvn clean package
java -jar target/dispatcher-*-jar-with-dependencies.jar

# Worker (в другом терминале)
cd worker
mvn clean package
java -jar target/worker-*-jar-with-dependencies.jar 8081 http://localhost:8080 worker-1
```

## Пример использования

### Синхронный вызов

```java
import ru.nsu.client.Cloud;
import ru.nsu.client.CloudClient;
import ru.nsu.client.RemoteFunction;

CloudClient client = Cloud.createClient("http://localhost:8080");

RemoteFunction<Integer> sum = Cloud.remoteFunction(
    client,
    SimpleCalculator.class,
    "sum",
    Integer.class,
    Integer.class
);

Integer result = sum.call(32, 44);
```

### Асинхронный вызов

```java
CompletableFuture<Integer> future = sum.callAsync(32, 44);
Integer result = future.get();
```

### Распределенный map

```java
DistributedMap map = Cloud.distributedMap(
    client,
    SimpleCalculator.class,
    "square"
);

List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
List<Integer> squared = map.map(numbers, Integer.class);
```

## API Endpoints

### Dispatcher

- `POST /api/workers/register` - регистрация worker
- `POST /api/workers/heartbeat` - heartbeat от worker
- `GET /api/workers` - список worker-ов
- `POST /api/tasks/submit` - отправка задачи
- `GET /api/tasks/{taskId}` - получение результата

### Worker

- `POST /api/tasks/execute` - получение задачи от dispatcher

## Технологии

- Java 21
- Maven
- Jackson (JSON)
- Lombok
- SLF4j + Logback

