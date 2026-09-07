package io.seoleir.engram.runtime.registry;


import io.seoleir.engram.api.annotation.OnEvent;
import io.seoleir.engram.api.annotation.WorkflowInterface;
import io.seoleir.engram.api.state.Decision;
import io.seoleir.engram.api.state.WorkflowState;
import io.seoleir.engram.core.codec.StateCodec;
import io.seoleir.engram.runtime.exception.RegistrationException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public final class WorkflowDefinitionParser {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private WorkflowDefinitionParser() {
    }

    @SuppressWarnings("unchecked")
    public static <S extends WorkflowState> WorkflowDefinition<S> parse(Class<?> workflowClass,
                                                                        Object target,
                                                                        S initialState,
                                                                        StateCodec codec) {
        String type = resolveType(workflowClass);
        validateClass(workflowClass, target);

        Map<String, WorkflowDefinition.Handler> handlers = new HashMap<>();
        Map<String, Method> sources = new HashMap<>();   // for duplicate diagnostics
        Class<?> stateType = null;

        for (Method method : workflowClass.getMethods()) {
            OnEvent onEvent = method.getAnnotation(OnEvent.class);
            if (onEvent == null) continue;
            if (method.isSynthetic() || method.isBridge()) continue;

            validateHandler(workflowClass, method);

            stateType = method.getParameterTypes()[0];

            String eventType = onEvent.value();
            validateEventType(eventType, method, workflowClass);

            Method previous = sources.put(eventType, method);
            checkIfPreviousHandlerIsNull(previous, method, eventType, workflowClass);

            handlers.put(eventType,
                    new WorkflowDefinition.Handler(unReflect(workflowClass, method), method.getParameterTypes()[1]));
        }

        validateHandlers(handlers, workflowClass);
        validateStateType(stateType, workflowClass, initialState);

        verifySerializable(workflowClass, initialState, codec);

        return new WorkflowDefinition<>(type, (Class<S>) stateType, initialState, target, handlers);
    }

    private static void validateHandlers(Map<String, WorkflowDefinition.Handler> handlers, Class<?> workflowClass) {
        if (handlers.isEmpty()) {
            throw new RegistrationException(
                    workflowClass.getName() + ": no @OnEvent handlers found. "
                            + "Check that handler methods are public and the annotation has RUNTIME retention.");
        }
    }

    private static void validateEventType(String eventType, Method method, Class<?> workflowClass) {
        if (eventType == null || eventType.isBlank()) {
            throw new RegistrationException(workflowClass.getName() + "." + method.getName() + ": @OnEvent value must not be blank");
        }
    }


    private static <S extends WorkflowState> void validateStateType(Class<?> stateType, Class<?> workflowClass, S initialState) {
        if (!WorkflowState.class.isAssignableFrom(stateType)) {
            throw new RegistrationException(
                    workflowClass.getName() + ": state type " + stateType.getSimpleName() + " must implement WorkflowState");
        }

        if (!stateType.isInstance(initialState)) {
            throw new RegistrationException(
                    workflowClass.getName() + ": initial state is " + initialState.getClass().getSimpleName()
                            + ", but handlers expect " + stateType.getSimpleName());
        }
    }

    private static void checkIfPreviousHandlerIsNull(Method previous,
                                                     Method next,
                                                     String eventType,
                                                     Class<?> workflowClass) {
        if (previous != null) {
            throw new RegistrationException(
                    workflowClass.getName() + ": two handlers for event '" + eventType + "' — "
                            + previous.getName() + " and " + next.getName());
        }
    }

    // check that annotation @WorkflowInterface presents
    private static String resolveType(Class<?> workflowClass) {
        WorkflowInterface annotation = workflowClass.getAnnotation(WorkflowInterface.class);
        if (annotation == null) {
            throw new RegistrationException(
                    workflowClass.getName() + " is not annotated with @WorkflowInterface");
        }
        return annotation.type().isBlank() ? workflowClass.getSimpleName() : annotation.type();
    }

    // basic validating
    private static void validateClass(Class<?> workflowClass, Object target) {
        if (!Modifier.isPublic(workflowClass.getModifiers())) {
            throw new RegistrationException(
                    workflowClass.getName() + " must be public");
        }
        if (workflowClass.isInterface() || Modifier.isAbstract(workflowClass.getModifiers())) {
            throw new RegistrationException(
                    workflowClass.getName() + " must be a concrete class, not an interface " + "or abstract class");
        }
        if (!workflowClass.isInstance(target)) {
            throw new RegistrationException(
                    "Instance of " + target.getClass().getName() + " is not compatible with " + workflowClass.getName());
        }
    }

    private static void validateHandler(Class<?> owner, Method m) {
        String where = owner.getName() + "." + m.getName();

        if (Modifier.isStatic(m.getModifiers())) {
            throw new RegistrationException(where + ": handler must not be static");
        }
        if (m.getParameterCount() != 2) {
            throw new RegistrationException(where + ": expected two parameters (state, payload), found " + m.getParameterCount());
        }
        if (!Decision.class.isAssignableFrom(m.getReturnType())) {
            throw new RegistrationException(where + ": must return Decision, found " + m.getReturnType().getSimpleName());
        }

        Class<?> payloadType = m.getParameterTypes()[1];
        if (payloadType.isPrimitive()) {
            throw new RegistrationException(where + ": payload parameter must not be primitive");
        }
    }

    private static MethodHandle unReflect(Class<?> owner, Method method) {
        try {
            return LOOKUP.unreflect(method);
        } catch (IllegalAccessException e) {
            throw new RegistrationException(
                    owner.getName() + "." + method.getName() + " is not accessible", e);
        }
    }

    private static void verifySerializable(Class<?> owner, WorkflowState state, StateCodec codec) {
        try {
            codec.encode(state);
        } catch (Exception e) {
            throw new RegistrationException(
                    owner.getName() + ": state type " + state.getClass().getSimpleName()
                            + " is not serializable by the configured codec. "
                            + "State must be plain data — no service references, lambdas or lazy collections.", e);
        }
    }
}
