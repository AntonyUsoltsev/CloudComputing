package ru.nsu.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class DynamicClassLoader extends ClassLoader {
    private final Map<String, Class<?>> classCache = new HashMap<>();

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Загружает класс из байткода.
     * @param className имя класса
     * @param classBytes байткод класса
     * @return загруженный класс
     * @throws ClassFormatError если байткод невалидный
     */
    public Class<?> loadClassFromBytes(String className, byte[] classBytes) throws ClassFormatError {
        if (classCache.containsKey(className)) {
            log.debug("Class {} found in cache", className);
            return classCache.get(className);
        }

        log.debug("Loading class {} from {} bytes", className, classBytes.length);
        
        // Проверяем минимальный размер класса (магическое число CAFEBABE + версия + минимум данных)
        if (classBytes.length < 8) {
            throw new ClassFormatError("Class file too small: " + classBytes.length + " bytes. Minimum is 8 bytes.");
        }

        if (classBytes[0] != (byte)0xCA || classBytes[1] != (byte)0xFE || 
            classBytes[2] != (byte)0xBA || classBytes[3] != (byte)0xBE) {
            throw new ClassFormatError("Invalid class file: missing magic number (0xCAFEBABE)");
        }

        Class<?> clazz = defineClass(className, classBytes, 0, classBytes.length);
        classCache.put(className, clazz);
        log.debug("Class {} loaded and cached successfully", className);
        
        return clazz;
    }

    public boolean isClassCached(String className) {
        return classCache.containsKey(className);
    }
}

