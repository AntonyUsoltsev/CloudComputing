package ru.nsu.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicClassLoaderTest {
    private DynamicClassLoader classLoader;

    public static class DummyClass {
    }

    @BeforeEach
    void setUp() {
        classLoader = new DynamicClassLoader(Thread.currentThread().getContextClassLoader());
    }

    @Test
    void testLoadClassFromBytes() {
        assertDoesNotThrow(() ->
                classLoader.loadClassFromBytes(
                        DummyClass.class.getName(),
                        createValidClassBytes(),
                        "test-hash"
                )
        );
    }

    @Test
    void testLoadClassCaching() {
        assertFalse(classLoader.isClassCached("test-hash"));
        assertDoesNotThrow(() ->
                classLoader.loadClassFromBytes(
                        DummyClass.class.getName(),
                        createValidClassBytes(),
                        "test-hash"
                )
        );
        assertTrue(classLoader.isClassCached("test-hash"));
    }

    @Test
    void testLoadClassWithInvalidMagicNumber() {
        String className = "DummyClass";
        byte[] invalidClassBytes = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

        assertThrows(ClassFormatError.class, () -> {
            classLoader.loadClassFromBytes(className, invalidClassBytes, "test-hash");
        });
    }

    @Test
    void testLoadClassWithTooSmallBytes() {
        String className = "TestClass";
        byte[] tooSmallBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA};

        assertThrows(ClassFormatError.class, () -> {
            classLoader.loadClassFromBytes(className, tooSmallBytes, "test-hash-1");
        });
    }

    @Test
    void testClassIsNotCached() {
        String className = "TestClass";
        byte[] tooSmallBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA};

        assertFalse(classLoader.isClassCached(className));
        assertThrows(ClassFormatError.class, () -> {
            classLoader.loadClassFromBytes(className, tooSmallBytes, "test-hash");
        });
        assertFalse(classLoader.isClassCached(className));
    }

    private byte[] createValidClassBytes() throws IOException {
        byte[] classBytes;
        try (InputStream is =
                     DummyClass.class
                             .getClassLoader()
                             .getResourceAsStream(
                                     "ru/nsu/worker/DynamicClassLoaderTest$DummyClass.class"
                             )) {

            assertNotNull(is);
            classBytes = is.readAllBytes();
        }
        return classBytes;
    }
}
