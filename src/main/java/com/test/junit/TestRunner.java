package com.test.junit;

import com.test.junit.anotation.*;
import com.test.junit.assertion.AssertionsRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class TestRunner {

    private final List<Class<?>> testClasses = new ArrayList<>();

    public void register(Class<?> testClass) {
        testClasses.add(testClass);
    }

    public void register(Class<?>... testClasses) {
        this.testClasses.addAll(Arrays.asList(testClasses));
    }

    public void run() {
        testClasses.forEach(TestRunner::processTest);
    }

    private static void processTest(Class<?> test) {

        Object instance = createInstance(test);
        Method[] methods = test.getMethods();
        List<Method> testMethods = findMethodByAnnotation(methods, Test.class);
        List<Method> beforeEachMethods = findMethodByAnnotation(methods, BeforeMethod.class);
        List<Method> beforeAllMethods = findMethodByAnnotation(methods, BeforeAll.class);
        List<Method> afterEachMethods = findMethodByAnnotation(methods, AfterMethod.class);
        List<Method> afterAllMethods = findMethodByAnnotation(methods, AfterAll.class);

        invokeMethods(instance, beforeAllMethods);
        invokeTestMethods(instance, beforeEachMethods, testMethods, afterEachMethods);
        invokeMethods(instance, afterAllMethods);
    }

    private static Object createInstance(Class<?> test) {
        try {
            Constructor<?> constructor = test.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Method> findMethodByAnnotation(Method[] methods, Class<? extends Annotation> annotation) {
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(annotation))
                .toList();
    }

    private static void invokeMethods(Object instance, List<Method> methods) {
        methods.forEach(method -> {
            try {
                method.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void invokeTestMethods(Object instance, List<Method> beforeEachMethods,
                                          List<Method> methods, List<Method> afterEachMethods) {
        methods.forEach(method -> {
            method.setAccessible(true);
            invokeMethods(instance, beforeEachMethods);

            if (method.isAnnotationPresent(Timeout.class)) {
                try {
                    invokeTimeoutTestMethod(method, instance);
                } catch (TimeoutException e) {
                    handleTimeoutException(method, e);
                }
            } else {
                invokeTestMethod(method, instance);
            }

            invokeMethods(instance, afterEachMethods);
            handleSunnyDayScenario(method);
        });
    }

    private static void invokeTestMethod(Method method, Object instance) {
        try {
            method.invoke(instance);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof AssertionsRuntimeException) {
                AssertionsRuntimeException ae = (AssertionsRuntimeException) e.getCause();
                handleAssertionException(method, ae);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void invokeTimeoutTestMethod(Method method, Object instance) throws TimeoutException {
        var timeBeforeInv = System.currentTimeMillis();
        var testMethodInvokeFuture = CompletableFuture.runAsync(() -> invokeTestMethod(method, instance));

        if (method.isAnnotationPresent(Timeout.class)) {
            var timeoutMillis = getTimeoutInMillis(method);
            while (!testMethodInvokeFuture.isDone()) {
                var currentTimeOfMethodInv = System.currentTimeMillis() - timeBeforeInv;
                if (currentTimeOfMethodInv >= timeoutMillis) {
                    throw new TimeoutException(String.format("Execute exceeded maximum time = %s s", timeoutMillis / 1000L));
                }
            }
        } else {
            try {
                testMethodInvokeFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static long getTimeoutInMillis(Method method) {
        var timeoutAnnot = method.getAnnotation(Timeout.class);
        var timeout = timeoutAnnot.time();
        var timeUnit = timeoutAnnot.timeUnit();
        long timeoutMillis;
        if (timeUnit == TimeUnit.SECONDS) {
            timeoutMillis = timeout * 1000L;
        } else if (timeUnit == TimeUnit.MINUTES) {
            timeoutMillis = timeout * 1000L * 60L;
        } else if (timeUnit == TimeUnit.MILLISECOND) {
            timeoutMillis = timeout;
        } else {
            throw new RuntimeException();
        }
        return timeoutMillis;
    }

    private static void handleAssertionException(Method method, AssertionsRuntimeException e) {
        System.out.println(ConsoleColors.RED);
        var description = getTestMethodDescription(method);
        if (description != null) {
            System.out.printf("[Test method %s description]  %s%n", method.getName(), description);
        }
        System.out.println(String.format("[Test method %s] is failed. Expected = [%s]; actual = [%s]", method.getName(), e.getExpected(), e.getActual()));
        System.out.println(ConsoleColors.RESET);
    }

    private static void handleSunnyDayScenario(Method method) {
        System.out.println(ConsoleColors.GREEN);
        var description = getTestMethodDescription(method);
        if (description != null) {
            System.out.printf("[Test method %s description]  %s%n", method.getName(), description);
        }
        System.out.println(String.format("[Test method %s] is successful", method.getName()));
        System.out.println(ConsoleColors.RESET);
    }

    private static void handleTimeoutException(Method method, TimeoutException e) {
        System.out.println(ConsoleColors.RED);
        var description = getTestMethodDescription(method);
        if (description != null) {
            System.out.printf("[Test method %s description]  %s%n", method.getName(), description);
        }
        System.out.printf("[Test method %s] is failed. %s%n", method.getName(), e.getMessage());
        System.out.println(ConsoleColors.RESET);
    }

    private static String getTestMethodDescription(Method method) {
        var description = method.getAnnotation(Description.class);
        return description != null ? description.message() : null;
    }
}
