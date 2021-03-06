package ru.coderedwolf.easy.rpc.socket.support;

import org.jetbrains.annotations.Nullable;
import org.springframework.util.Assert;
import ru.coderedwolf.easy.rpc.socket.Message;
import ru.coderedwolf.easy.rpc.socket.MessageHeaders;
import ru.coderedwolf.easy.rpc.socket.MessageType;
import ru.coderedwolf.easy.rpc.socket.jsonRpc.annotation.Subscribe;

import java.util.Map;

/**
 * This class allows to modify message headers.
 *
 * @author CodeRedWolf
 * @since 1.0
 */
public class MessageHeaderAccessor {

    /**
     * Session id for message.
     */
    public final static String SESSION_ID = "sessionId";

    /**
     * The name of subscribe method, this field use for method with {@link Subscribe}
     * this field is bound to send notification
     */
    public final static String SUBSCRIBE_METHOD = "subscribeMethodName";

    /**
     * The name of method to be accessed from the request
     */
    public final static String MESSAGE_METHOD = "messageMethod";

    /**
     * Name of message that user for notification
     */
    public final static String SEND_MESSAGE_METHOD = "sendMessageMethod";

    private final MuttableHeaders messageHeaders;

    private MessageHeaderAccessor(MessageHeaders messageHeaders) {
        this.messageHeaders = new MuttableHeaders(messageHeaders);
    }

    private MessageHeaderAccessor(Message<?> message) {
        this.messageHeaders = new MuttableHeaders(message.getMessageHeader());
    }

    /**
     * @return instance from message headers
     */
    public static MessageHeaderAccessor ofHeaders(MessageHeaders messageHeaders) {
        return new MessageHeaderAccessor(messageHeaders);
    }

    /**
     * @return instance from message method
     */
    public static MessageHeaderAccessor ofMessage(Message<?> message) {
        Assert.notNull(message, "message required");
        return new MessageHeaderAccessor(message);
    }

    /**
     * @return subscribe method from message headers
     */
    @Nullable
    public static String getSubscribeMethod(MessageHeaders messageHeaders) {
        Object subscribeName = messageHeaders.get(SUBSCRIBE_METHOD);
        if (subscribeName != null) {
            return (String) subscribeName;
        }
        return null;
    }

    /**
     * @return message method method from message headers
     */
    @Nullable
    public static String getMessageMethod(MessageHeaders messageHeaders) {
        Object messageMethod = messageHeaders.get(MESSAGE_METHOD);
        if (messageMethod != null) {
            return (String) messageMethod;
        }
        return null;
    }

    /**
     * @return message send message method method from message headers
     */
    @Nullable
    public static String getSendMessageMethod(MessageHeaders messageHeaders) {
        Object messageMethod = messageHeaders.get(SEND_MESSAGE_METHOD);
        if (messageMethod != null) {
            return (String) messageMethod;
        }
        return null;
    }

    /**
     * @return session id from message headers
     */
    @Nullable
    public static String getSessionId(MessageHeaders messageHeaders) {
        Object messageMethod = messageHeaders.get(SESSION_ID);
        if (messageMethod != null) {
            return (String) messageMethod;
        }
        return null;
    }

    public void setMessageType(MessageType messageType) {
        this.messageHeaders.getRawHeader().put(MessageHeaders.MESSAGE_TYPE, messageType);
    }

    public void setMessageMethod(String method) {
        this.messageHeaders.getRawHeader().put(MESSAGE_METHOD, method);
    }

    public void setSubscribeName(String method) {
        this.messageHeaders.getRawHeader().put(SUBSCRIBE_METHOD, method);
    }

    public void setSendMessageMethod(String sendMessageMethod) {
        this.messageHeaders.getRawHeader().put(SEND_MESSAGE_METHOD, sendMessageMethod);
    }

    public void setSessionId(String sessionId) {
        this.messageHeaders.getRawHeader().put(SESSION_ID, sessionId);
    }

    public MessageType messageType() {
        return (MessageType) this.messageHeaders.getRawHeader().get(MessageHeaders.MESSAGE_TYPE);
    }

    public String getMessageMethod() {
        Object header = getHeader(MESSAGE_METHOD);
        return header != null ? (String) header : "";
    }

    public String getSubscribeMethod() {
        Object header = getHeader(SUBSCRIBE_METHOD);
        return header != null ? (String) header : null;
    }

    public String getSendMessageMethod() {
        Object header = getHeader(SEND_MESSAGE_METHOD);
        return header != null ? (String) header : null;
    }

    public String getSessionId() {
        Object header = getHeader(SESSION_ID);
        return header != null ? (String) header : null;
    }

    public Object getHeader(String key) {
        return this.messageHeaders.get(key);
    }

    public void putHeader(String key, Object value) {
        this.messageHeaders.getRawHeader().put(key, value);
    }

    public void setImmutable() {
        this.messageHeaders.setImuttable();
    }

    public MessageHeaders getMessageHeaders() {
        if (messageHeaders.muttable) {
            messageHeaders.setImuttable();
        }
        return this.messageHeaders;
    }

    private static class MuttableHeaders extends MessageHeaders {

        private boolean muttable = true;

        private MuttableHeaders(Map<String, Object> headers) {
            super(headers);
        }

        private void setImuttable() {
            this.muttable = false;
        }

        @Override
        protected Map<String, Object> getRawHeader() {
            Assert.state(muttable, "Already immutable");
            return super.getRawHeader();
        }
    }
}
