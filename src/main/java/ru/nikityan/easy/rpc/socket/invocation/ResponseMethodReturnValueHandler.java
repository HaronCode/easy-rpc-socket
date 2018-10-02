package ru.nikityan.easy.rpc.socket.invocation;

import org.jetbrains.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import ru.nikityan.easy.rpc.socket.Message;
import ru.nikityan.easy.rpc.socket.MessageHeaders;
import ru.nikityan.easy.rpc.socket.core.MessageSendingOperations;
import ru.nikityan.easy.rpc.socket.jsonRpc.JsonRpcResponse;
import ru.nikityan.easy.rpc.socket.jsonRpc.annotation.Method;
import ru.nikityan.easy.rpc.socket.support.MessageBuilder;
import ru.nikityan.easy.rpc.socket.support.MessageHeaderAccessor;

/**
 * Created by Nikit on 30.09.2018.
 */
public class ResponseMethodReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final MessageSendingOperations messageSendingOperations;

    public ResponseMethodReturnValueHandler(MessageSendingOperations messageSendingOperations) {
        this.messageSendingOperations = messageSendingOperations;
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return returnType.hasMethodAnnotation(Method.class);
    }

    @Override
    public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType,
                                  Message<?> message) throws Exception {

        Assert.notNull(returnType, "Method parameter is required");
        MessageHeaders messageHeader = message.getMessageHeader();
        MessageHeaderAccessor accessor = MessageHeaderAccessor.ofHeaders(messageHeader);
        Method annotation = returnType.getMethodAnnotation(Method.class);
        accessor.setSendMessageMethod(annotation.value());
        messageHeader = accessor.getMessageHeaders();
        JsonRpcResponse rpcResponse = new JsonRpcResponse(messageHeader.getId(), returnValue);
        Message<JsonRpcResponse> responseMessage = MessageBuilder.fromPayload(rpcResponse)
                .withHeaders(messageHeader)
                .build();
        messageSendingOperations.send(responseMessage);
    }
}
