package io.seoleir.engram.runtime.util;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ClassScanner {

    public static Set<Class<?>> findAnnotatedClass(String packageName, Class<? extends Annotation> annotation) {
        Set<Class<?>> classes = new HashSet<>();

        String classPath = System.getProperty("java.class.path");
        String[] entries = classPath.split(File.pathSeparator);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        for (String entry : entries) {
            Path path = Path.of(entry);

            if (Files.isDirectory(path)) {
                scanDirectory(path, path, classLoader, classes, annotation);
            } else {
                scanJar(path, classLoader, classes, annotation);
            }
        }

        return classes;
    }

    private static void scanDirectory(Path root, Path current,
                                      ClassLoader classLoader, Set<Class<?>> classes,
                                      Class<? extends Annotation> annotation) {
        try (Stream<Path> stream = Files.walk(current)) {
            stream.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String className = root.relativize(path).toString()
                                .replace(File.separatorChar, '.')
                                .replace(".class", "");

                        loadClassIfAnnotationPresents(className, classLoader, classes, annotation);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void scanJar(Path jarPath,
                                ClassLoader classLoader, Set<Class<?>> classes,
                                Class<? extends Annotation> annotation) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {

            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();

                if (!jarEntry.getName().endsWith(".class")) {
                    continue;
                }

                String className = jarEntry.getName()
                        .replace("/", ".")
                        .replace(".class", "");

                loadClassIfAnnotationPresents(className, classLoader, classes, annotation);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void loadClassIfAnnotationPresents(String className,
                                                      ClassLoader classLoader, Set<Class<?>> classes,
                                                      Class<? extends Annotation> annotation) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);

            if (clazz.isAnnotationPresent(annotation)) {
                classes.add(clazz);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
