package ru.nsu.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Класс-загрузчик для динамической загрузки классов из байткода.
 * Кэширует загруженные классы по codeHash для оптимизации.
 */
@Slf4j
public class DynamicClassLoader extends ClassLoader {
    private final Map<String, Class<?>> classCache = new HashMap<>();

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Загружает класс из байткода.
     * Если класс с таким codeHash уже загружен, возвращает его из кэша.
     * @param className имя класса
     * @param classBytes байткод класса
     * @param codeHash хеш байткода для проверки кэша
     * @return загруженный класс
     * @throws ClassFormatError если байткод невалидный
     */
    public Class<?> loadClassFromBytes(String className, byte[] classBytes, String codeHash) throws ClassFormatError {
        String cacheKey = codeHash != null ? codeHash : className;

        if (isClassCached(cacheKey)) {
            log.debug("Class {} with hash {} found in cache", className, codeHash);
            return classCache.get(cacheKey);
        }

        log.debug("Loading class {} from {} bytes (hash: {})", className, classBytes.length, codeHash);
        
        // Проверяем минимальный размер класса (CAFEBABE + версия + минимум данных)
        if (classBytes.length < 8) {
            throw new ClassFormatError("Class file too small: " + classBytes.length + " bytes. Minimum is 8 bytes.");
        }

        if (classBytes[0] != (byte)0xCA || classBytes[1] != (byte)0xFE || 
            classBytes[2] != (byte)0xBA || classBytes[3] != (byte)0xBE) {
            throw new ClassFormatError("Invalid class file: missing magic number (0xCAFEBABE)");
        }

        Class<?> clazz = defineClass(className, classBytes, 0, classBytes.length);
        classCache.put(cacheKey, clazz);
        log.debug("Class {} loaded and cached successfully (hash: {})", className, codeHash);
        
        return clazz;
    }

    public boolean isClassCached(String cacheKey) {
        return cacheKey != null && classCache.containsKey(cacheKey);
    }
}

