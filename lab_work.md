# Лабораторная работа: Java Virtual Threads

## Цель

Понять на работающем коде различие между platform threads и virtual threads, а также связь blocking I/O, CPU, carrier threads, concurrency и parallelism.

## Термины

- **Задача (task)** — `Runnable`, отправленный в `ExecutorService`.
- **Platform thread** — обычный Java-поток, соответствующий потоку ОС и планируемый ОС.
- **Virtual thread** — лёгкий поток, которым управляет JVM.
- **Carrier thread** — platform thread, на котором JVM на время исполняет virtual thread.
- **Blocking I/O** — ожидание сети, файла, базы данных или таймера, когда задача не может продолжить работу до события.

## Эксперимент 1 — fixed thread pool: 10 потоков

### Код

```java
package org;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final int NUMBER_OF_TASKS = 1_000;
    private static final int POOL_SIZE = 10;

    public static void main(String[] args) {
        Instant start = Instant.now();

        try (ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE)) {
            for (int i = 0; i < NUMBER_OF_TASKS; i++) {
                executor.submit(Main::fakeHttpRequest);
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        System.out.println("Done in " + duration.toMillis() + " ms");
    }

    private static void fakeHttpRequest() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task was interrupted", exception);
        }
    }
}
```

### Prediction до запуска

1. Примерно пару минут.
2. «Лочается весь поток и ждёт окончания sleep, другой не может его перехватить».
3. CPU будет сильно загружен.

### Фактический результат

```text
Done in PT1M40.450425S
```

### Разбор

`newFixedThreadPool(10)` создаёт максимум 10 worker threads. Одновременно начать выполнение могут 10 задач. Остальные остаются в очереди executor'а.

```text
1 000 задач / 10 потоков = 100 волн
100 волн * 1 секунда = примерно 100 секунд
```

`Thread.sleep` почти не использует CPU, но platform thread остаётся занятым текущей задачей: executor не может выдать ему следующую задачу. Слово «lock» здесь неточно: это не блокировка на мониторе, а ожидание таймера при занятом worker thread.

### Мои выводы

> 10 задач, потому что пул size 10 и запущено 10 потоков; 1 000 задач / 10 потоков = 100 волн.

> Потоки просто ждут окончания `sleep`.

## Эксперимент 2 — fixed thread pool: 100 потоков

### Изменение

```java
private static final int POOL_SIZE = 100;
```

### Фактический результат

```text
Done in PT10.055032S
```

### Разбор

```text
1 000 задач / 100 потоков = 10 волн
10 волн * 1 секунда = примерно 10 секунд
```

Одновременно ожидают до 100 задач. CPU по-прежнему почти свободен: работа в опыте — ожидание, а не вычисление.

### Мои выводы

> Если было 10 потоков на 1 000 задач и около 100 секунд, то при 100 потоках получается около 10 секунд.

## Эксперимент 3 — fixed thread pool: 1 000 platform threads

### Изменение

```java
private static final int POOL_SIZE = 1_000;
```

### Фактический результат

```text
Done in PT1.054274S
```

### Разбор

Все 1 000 задач почти одновременно начали `sleep`, поэтому одна секунда ожидания перекрылась. Но это означает создание 1 000 platform/OS threads.

В production это плохо масштабируется: каждый поток ОС требует native-ресурсов и стека, имеет стоимость создания и планирования, а ОС имеет лимиты на число потоков. Результат около секунды сам по себе не делает такой подход хорошей backend-стратегией.

### Мои выводы

> Обычный поток потребляет память, и это нужно учитывать.

## Эксперимент 4 — virtual thread per task: 1 000 задач

### Изменение executor'а

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
```

`POOL_SIZE` после этого не нужен: executor создаёт одну virtual thread на каждую отправленную задачу.

### Prediction до запуска

1. Ожидалось 10–30 секунд.
2. Будет создано 1 000 virtual threads.
3. Предполагалось, что JVM создаст 1 000 OS threads.

### Фактический результат

```text
Done in PT1.02615S
```

### Разбор

Virtual thread во время поддерживаемого blocking operation, такого как `Thread.sleep`, может быть снят JVM с carrier thread. Carrier thread освобождается для другой готовой virtual-задачи. Поэтому 1 000 ожидающих virtual threads не требуют 1 000 OS threads.

Virtual threads не ускоряют CPU: они помогают масштабировать большое число одновременно ожидающих задач.

### Мои выводы

> Virtual threads управляются JVM, а platform threads — системой.

> JVM выполняет другие virtual-задачи, пока одна ждёт.

## Эксперимент 5 — 10 000 virtual threads

### Изменение

```java
private static final int NUMBER_OF_TASKS = 10_000;
```

### Фактический результат

```text
Done in PT1.057976S
```

### Разбор

Рост количества задач в 10 раз почти не изменил wall-clock time: virtual threads быстро дошли до `sleep` и почти одновременно ожидали таймер. Это concurrency — множество незавершённых задач в системе. Оно не равно parallelism — одновременному вычислению на ядрах CPU.

### Мои выводы

> JVM быстро доводит задачи до остановки; пока одни ждут, выполняются другие — получается конвейер.

## Эксперимент 6 — 100 000 virtual threads

### Изменение

```java
private static final int NUMBER_OF_TASKS = 100_000;
```

### Фактический результат

```text
Done in PT1.627245S
```

### Разбор

В коде нет настоящего I/O: `Thread.sleep(1_000)` только имитирует ожидание. Базовая одна секунда почти не изменилась, потому что ожидания перекрываются. Дополнительные примерно 0.63 секунды — создание и планирование 100 000 virtual threads, запуск задач до `sleep`, их пробуждение и завершение.

100 000 platform threads были бы существенно дороже из-за стеков и native-ресурсов, затрат на создание и планирование потоков ОС, context switching и лимитов ОС.

### Мои выводы

> При virtual threads без сложных CPU-операций основная нагрузка и время уходят на расчёт и оркестрирование virtual threads.

> Одна секунда нужна для сна, а остальные миллисекунды — реальные затраты на выполнение и расчёт.

## Эксперимент 7 — virtual thread и carrier thread

### Наблюдательный код

```java
package org;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    private static final int TASKS = 12;

    public static void main(String[] args) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < TASKS; i++) {
                int taskNumber = i;

                executor.submit(() -> {
                    System.out.printf("Task %02d: before sleep — %s%n",
                            taskNumber, Thread.currentThread());

                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }

                    System.out.printf("Task %02d: after sleep  — %s%n",
                            taskNumber, Thread.currentThread());
                });
            }
        }
    }
}
```

### Наблюдение

```text
Task 00: before sleep — VirtualThread[#28]/runnable@ForkJoinPool-1-worker-1
Task 00: after sleep  — VirtualThread[#28]/runnable@ForkJoinPool-1-worker-4
```

У виртуальной задачи сохранился тот же ID `VirtualThread[#28]`, но она продолжилась на другом carrier worker. Часть `#28` — внутренний ID, а не число созданных в опыте потоков. При `TASKS = 12` executor создаёт 12 virtual threads.

Число carrier workers меньше числа virtual threads и они переиспользуются. Отображение `@ForkJoinPool-...-worker-N` — диагностическая деталь конкретной реализации JDK, полезная для наблюдения, но не публичный API-контракт.

### Мои выводы

> Virtual thread не поменялся, сменился worker.

> Ожидающий virtual thread не ждёт carrier: carrier переключается на готовую задачу.

> Virtual thread сохранился, и не важно, кто его несёт.

## Ключевая модель

```text
Много virtual threads
        ↓ JVM планирует и снимает ожидающие задачи
Небольшое число carrier threads
        ↓
Platform / OS threads
        ↓
Ограниченное число ядер CPU для реальных вычислений
```

## Следующий эксперимент — реальный blocking HTTP

Следующий шаг: заменить искусственный `Thread.sleep` на синхронный `HttpClient.send(...)`, оставить virtual thread per task и выполнить небольшое безопасное число запросов к endpoint с задержкой. После этого отдельно сравнить с CPU-bound задачей, где virtual threads не увеличивают число одновременно выполняемых вычислений.
