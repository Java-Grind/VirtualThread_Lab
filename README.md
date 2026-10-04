# Virtual Threads Lab

Небольшая лабораторная работа на Java, демонстрирующая, когда виртуальные потоки полезны: при blocking I/O и большом числе одновременно ожидающих задач. Также есть сравнение с CPU-bound нагрузкой.

## Установка и запуск

Нужны JDK 26 и Maven 3.9+.

```bash
git clone <repository-url>
cd VirtualThread_Lab
mvn compile
```

Запуск примеров:

```bash
java -cp target/classes org.Lab_Main
java -cp target/classes org.ThreadOutput_Main
java -cp target/classes org.BlockingHttp_Main
java -cp target/classes org.CpuBoundMain
```

Описание экспериментов — в [notes/lab_work.md](notes/lab_work.md).
