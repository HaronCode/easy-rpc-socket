package ru.nikityan.easy.rpc.socket.jsonRpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.support.StaticApplicationContext;
import ru.nikityan.easy.rpc.socket.Message;
import ru.nikityan.easy.rpc.socket.MessageChannel;
import ru.nikityan.easy.rpc.socket.SubscribeMessageChanel;
import ru.nikityan.easy.rpc.socket.jsonRpc.annotation.*;
import ru.nikityan.easy.rpc.socket.support.MessageBuilder;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.verify;

/**
 * Created by Nikit on 29.09.2018.
 */
public class JsonRpcAnnotationMessageHandlerTest {

    private TestJsonRpcAnnotationMessageHandler messageHandler;

    @Mock
    private SubscribeMessageChanel subscribeMessageChanel;

    @Mock
    private MessageChannel messageChannel;

    private TestController testController;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        messageHandler = new TestJsonRpcAnnotationMessageHandler(subscribeMessageChanel, messageChannel);
        messageHandler.setApplicationContext(new StaticApplicationContext());
        messageHandler.afterPropertiesSet();

        testController = new TestController();
    }

    @Test
    public void testSimpleRequest() throws Exception {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("x", 1);
        jsonObject.addProperty("y", 34L);
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(4, "request", jsonObject);
        Message<JsonRpcRequest> message = MessageBuilder
                .fromPayload(jsonRpcRequest)
                .build();

        messageHandler.registerHandler(testController);
        messageHandler.handleMessage(message);

        assertEquals("request", testController.method);
        assertEquals(1L, testController.arguments.get("x"));
        assertEquals(34L, testController.arguments.get("y"));
    }

    @Test
    public void testRequestCollection() throws Exception {
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(1);
        jsonArray.add(2);
        jsonArray.add(3);
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(4, "requestList", jsonArray);
        Message<JsonRpcRequest> message = MessageBuilder
                .fromPayload(jsonRpcRequest)
                .build();

        messageHandler.registerHandler(testController);
        messageHandler.handleMessage(message);

        assertEquals("requestList", testController.method);
        assertEquals(Arrays.asList(1, 2, 3), testController.arguments.get("input"));
    }

    @Test
    public void testSubscribeObj() throws Exception {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("x", 1);
        jsonObject.addProperty("y", 34L);

        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(4, "subscribeObj", jsonObject);
        Message<JsonRpcRequest> message = MessageBuilder
                .fromPayload(jsonRpcRequest)
                .build();

        messageHandler.registerHandler(testController);
        messageHandler.handleMessage(message);

        assertEquals("subscribeObj", testController.method);
        assertEquals(1L, testController.arguments.get("x"));
    }


    @Test
    public void testSubscribe() throws Exception {
        JsonPrimitive jsonObject = new JsonPrimitive(123);

        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(4, "subscribe", jsonObject);
        Message<JsonRpcRequest> message = MessageBuilder
                .fromPayload(jsonRpcRequest)
                .build();

        messageHandler.registerHandler(testController);
        messageHandler.handleMessage(message);

        assertEquals("subscribe", testController.method);
        assertEquals(123L, testController.arguments.get("id"));
    }


    @Test
    public void testRequestReturnObj() throws Exception {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", 1);
        jsonObject.addProperty("name", "foo");
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(4, "requestReturnObj", jsonObject);
        Message<JsonRpcRequest> message = MessageBuilder
                .fromPayload(jsonRpcRequest)
                .build();

        messageHandler.registerHandler(testController);
        messageHandler.handleMessage(message);

        assertEquals("requestReturnObj", testController.method);
        assertEquals(1L, testController.arguments.get("id"));
        assertEquals("foo", testController.arguments.get("name"));
    }


    @Test
    public void handleIfNotFoundMethod() throws Exception {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(4, "unknown", null);
        Message<JsonRpcRequest> message = MessageBuilder
                .fromPayload(jsonRpcRequest)
                .build();

        messageHandler.registerHandler(testController);
        messageHandler.handleMessage(message);

        verify(messageChannel).send(argThat(new BaseMatcher<Message<?>>() {
            @Override
            public boolean matches(Object item) {
                Message<?> sendMessage = (Message<?>) item;
                JsonRpcResponse rpcResponse = (JsonRpcResponse) sendMessage.getPayload();
                return rpcResponse.getError() != null;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Payload not match with json rpc error");
            }
        }));
    }

    @Test
    public void testExceptionHandler() throws Exception {
        JsonRpcRequest jsonRpcRequest = new JsonRpcRequest(4, "errorMethod", null);
        Message<JsonRpcRequest> message = MessageBuilder
                .fromPayload(jsonRpcRequest)
                .build();

        messageHandler.registerHandler(testController);
        messageHandler.handleMessage(message);

        assertEquals("exceptionHandler", testController.method);
    }

    private static class TestJsonRpcAnnotationMessageHandler extends JsonRpcAnnotationMessageHandler {

        public TestJsonRpcAnnotationMessageHandler(@NotNull SubscribeMessageChanel inboundChannel,
                                                   @NotNull MessageChannel outboundChannel) {
            super(inboundChannel, outboundChannel);
        }

        public void registerHandler(Object handler) {
            super.resolveHandlerMethods(handler);
        }
    }

    @Controller
    private static class TestController {

        private String method;

        private Map<String, Object> arguments = new LinkedHashMap<>();

        @RequestMapping("requestList")
        public String requestList(@Param List<Integer> input) {
            method = "requestList";
            arguments.put("input", input);
            return "ok";
        }

        @SubscribeMapping("subscribeObj")
        public Answer subscribeObj(@Param("x") long id) {
            method = "subscribeObj";
            arguments.put("x", id);
            return new Answer("subscribeObj", id);
        }

        @SubscribeMapping("subscribe")
        public String subscribe(@Param long id) {
            method = "subscribe";
            arguments.put("id", id);
            return "ok";
        }

        @RequestMapping("request")
        public String request(@Param("x") long x, @Param("y") Long a) {
            method = "request";
            arguments.put("x", x);
            arguments.put("y", a);
            return "ok";
        }

        @RequestMapping("requestReturnObj")
        public Answer requestReturnObj(@Param("id") long id, @Param("name") String name) {
            method = "requestReturnObj";
            arguments.put("id", id);
            arguments.put("name", name);
            return new Answer(name, id);
        }

        @RequestMapping("errorMethod")
        public void errorMethod() throws Exception {
            throw new Exception("internal error");
        }

        @ExceptionHandler
        public String exceptionHandler(Exception ex) {
            method = "exceptionHandler";
            arguments.put("ex", ex);
            return ex.getLocalizedMessage();
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public Answer exceptionHandlerRetrunObj(Exception ex) {
            method = "exceptionHandler";
            arguments.put("ex", ex);
            return new Answer(ex.getLocalizedMessage(), -1);
        }
    }

    private static class Answer {
        private final String name;
        private final long id;

        public Answer(String name, long id) {
            this.name = name;
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public long getId() {
            return id;
        }
    }
}