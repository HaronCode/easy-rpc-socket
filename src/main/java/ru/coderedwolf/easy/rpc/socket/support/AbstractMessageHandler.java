package ru.coderedwolf.easy.rpc.socket.support;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import ru.coderedwolf.easy.rpc.socket.Message;
import ru.coderedwolf.easy.rpc.socket.MessageHandler;
import ru.coderedwolf.easy.rpc.socket.exceptions.MessagingException;
import ru.coderedwolf.easy.rpc.socket.handler.AbstractExceptionHandlerMethodResolver;
import ru.coderedwolf.easy.rpc.socket.handler.HandlerMethod;
import ru.coderedwolf.easy.rpc.socket.handler.InvocableHandlerMethod;
import ru.coderedwolf.easy.rpc.socket.handler.resolvers.ArgumentResolver;
import ru.coderedwolf.easy.rpc.socket.handler.resolvers.ArgumentResolverComposite;
import ru.coderedwolf.easy.rpc.socket.invocation.HandlerMethodReturnValueHandler;
import ru.coderedwolf.easy.rpc.socket.invocation.HandlerMethodReturnValueHandlerComposite;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for HandlerMethod-based message handling. Provides most of
 * the logic required to discover handler methods at startup, find a matching handler
 * method at runtime for a given message and invoke it.
 *
 * @author CodeRedWolf
 * @since 1.0
 */
public abstract class AbstractMessageHandler implements MessageHandler, ApplicationContextAware, InitializingBean {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

    @Nullable
    private ApplicationContext applicationContext;

    private final ArgumentResolverComposite argumentResolverComposite
            = new ArgumentResolverComposite();

    private final Map<Class<?>, AbstractExceptionHandlerMethodResolver> exceptionHandlerCache
            = new ConcurrentHashMap<>(64);

    private final Map<String, HandlerMethod> handlerMethods = new LinkedHashMap<>(64);

    private final HandlerMethodReturnValueHandlerComposite returnValueHandlers =
            new HandlerMethodReturnValueHandlerComposite();

    @Override
    public void afterPropertiesSet() throws Exception {
        if (argumentResolverComposite.getResolvers().isEmpty()) {
            argumentResolverComposite.addResolvers(initArgumentResolvers());
        }

        if (returnValueHandlers.getHandlers().isEmpty()) {
            returnValueHandlers.addHandlers(initMethodReturnValue());
        }

        if (applicationContext == null) {
            return;
        }

        for (String beanName : applicationContext.getBeanNamesForType(Object.class)) {
            if (beanName.contains(SCOPED_TARGET_NAME_PREFIX)) {
                continue;
            }
            Class<?> type = null;
            try {
                type = applicationContext.getType(beanName);
            } catch (Throwable throwable) {
                logger.debug("Could not get bean type for bean with name {}", beanName);
            }

            if (type != null && isHandler(type)) {
                resolveHandlerMethods(beanName);
            }
        }
    }

    /**
     * @return list of HandlerMethodReturnValueHandler
     */
    protected abstract List<HandlerMethodReturnValueHandler> initMethodReturnValue();

    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Handle incoming message.
     *
     * @param message the message to be handled
     * @throws MessagingException if fail handle message.
     */
    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        String destination = getDestination(message);
        if (destination == null) {
            return;
        }
        logger.debug("Searching methods to handle {} , destination='{}'", message, destination);
        handleMessageInternal(message, destination);
    }

    @Nullable
    public ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }

    /**
     * Provide the mapping for a handler method.
     *
     * @param method   the method for which is being created destination.
     * @param userType handler class.
     */
    protected abstract String getMappingForMethod(Method method, Class<?> userType);

    /**
     * @return list of argument resolver for specific handler.
     */
    protected abstract List<? extends ArgumentResolver> initArgumentResolvers();

    /**
     * @param beanType instance of class.
     * @return true if class is handler, false if not.
     */
    protected abstract boolean isHandler(Class<?> beanType);

    /**
     * Find destination for input message.
     *
     * @param message given message.
     * @return describe of destination.
     */
    protected abstract String getDestination(Message<?> message);

    /**
     * Create exception handler for given handler class.
     */
    protected abstract AbstractExceptionHandlerMethodResolver createExceptionHandlerMethodResolverFor(Class<?> beanType);

    /**
     * Handle request method for which not found handler.
     *
     * @param message     request massage.
     * @param destination the destination request.
     */
    protected abstract void handleNotFoundMethod(Message<?> message, String destination);

    /**
     * Handle unexpected exception.
     *
     * @param message request message.
     */
    protected abstract void handleDefaultError(Exception exception, Message<?> message);

    /**
     * Handle request message.
     */
    protected void handleMatch(HandlerMethod handlerMethod, String destination, Message<?> message) {
        logger.debug("Invoking {}", handlerMethod.getShortLogMessage());
        handlerMethod = handlerMethod.createWithResolvedBean();
        InvocableHandlerMethod invocable = new InvocableHandlerMethod(handlerMethod);
        invocable.setArgumentResolvers(this.argumentResolverComposite);
        try {
            Object returnValue = invocable.invoke(message);
            MethodParameter returnType = handlerMethod.getReturnType();
            this.returnValueHandlers.handleReturnValue(returnValue, returnType, message);
        } catch (Exception ex) {
            processHandlerMethodException(handlerMethod, ex, message);
        }
    }

    /**
     * Handle unexpected exception.
     *
     * @param handlerMethod exception method handler.
     * @param message       request message.
     */
    protected void processHandlerMethodException(HandlerMethod handlerMethod, Exception exception, Message<?> message) {
        InvocableHandlerMethod invocable = getExceptionHandlerMethod(handlerMethod, exception);
        if (invocable == null) {
            handleDefaultError(exception, message);
            return;
        }
        invocable.setArgumentResolvers(this.argumentResolverComposite);
        logger.debug("Invoking {}", invocable.getShortLogMessage());
        try {
            Throwable cause = exception.getCause();
            Object returnValue = (cause != null ?
                    invocable.invoke(message, exception, cause, handlerMethod) :
                    invocable.invoke(message, exception, handlerMethod));
            MethodParameter returnType = invocable.getReturnType();
            this.returnValueHandlers.handleReturnValue(returnValue, returnType, message);
        } catch (Throwable throwable) {
            logger.debug("Error while processing handler method exception", throwable);
            handleDefaultError(exception, message);
        }
    }

    protected void handleMessageInternal(Message<?> message, String destination) {
        HandlerMethod handlerMethod = this.handlerMethods.get(destination);
        if (handlerMethod == null) {
            handleNotFoundMethod(message, destination);
            return;
        }
        logger.debug("Invoking {}", handlerMethod.getShortLogMessage());
        handleMatch(handlerMethod, destination, message);
    }

    protected void resolveHandlerMethods(Object handler) {
        Class<?> handlerType;
        if (handler instanceof String) {
            ApplicationContext context = getApplicationContext();
            Assert.state(context != null, "ApplicationContext is required for resolving handler bean names");
            handlerType = context.getType((String) handler);
        } else {
            handlerType = handler.getClass();
        }
        if (handlerType == null) {
            return;
        }

        final Class<?> userType = ClassUtils.getUserClass(handlerType);
        Map<Method, String> methods = MethodIntrospector.selectMethods(userType,
                (MethodIntrospector.MetadataLookup<String>) method -> getMappingForMethod(method, userType));
        if (logger.isDebugEnabled()) {
            logger.debug(methods.size() + " message handler methods found on " + userType + ": " + methods);
        }
        methods.forEach((key, value) -> registerHandlerMethod(handler, key, value));
    }

    protected void registerHandlerMethod(Object handler, Method method, String mapping) {
        Assert.notNull(mapping, "Mapping must not be null");
        HandlerMethod newHandlerMethod = createHandlerMethod(handler, method);
        HandlerMethod oldHandlerMethod = this.handlerMethods.get(mapping);

        if (oldHandlerMethod != null && !oldHandlerMethod.equals(newHandlerMethod)) {
            throw new IllegalStateException("Ambiguous mapping found. Cannot map '" + newHandlerMethod.getBean() +
                    "' bean method \n" + newHandlerMethod + "\nto " + mapping + ": There is already '" +
                    oldHandlerMethod.getBean() + "' bean method\n" + oldHandlerMethod + " mapped.");
        }

        this.handlerMethods.put(mapping, newHandlerMethod);
        logger.debug("Mapped \"{}\", onto {}", mapping, newHandlerMethod);
    }

    private InvocableHandlerMethod getExceptionHandlerMethod(HandlerMethod handlerMethod, Exception exception) {
        if (logger.isDebugEnabled()) {
            logger.debug("Searching methods to handle " + exception.getClass().getSimpleName());
        }
        Class<?> beanType = handlerMethod.getBeanType();
        AbstractExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache
                .computeIfAbsent(beanType, key -> createExceptionHandlerMethodResolverFor(beanType));
        Method method = resolver.resolveMethod(exception);
        if (method != null) {
            return new InvocableHandlerMethod(handlerMethod.getBean(), method);
        }
        return null;
    }

    protected HandlerMethod createHandlerMethod(Object handler, Method method) {
        HandlerMethod handlerMethod;
        if (handler instanceof String) {
            ApplicationContext context = getApplicationContext();
            Assert.state(context != null, "ApplicationContext is required for resolving handler bean names");
            String beanName = (String) handler;
            handlerMethod = new HandlerMethod(beanName, context.getAutowireCapableBeanFactory(), method);
        } else {
            handlerMethod = new HandlerMethod(handler, method);
        }
        return handlerMethod;
    }
}
