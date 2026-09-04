package io.github.nnkwrik.imservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import fangxianyu.innerApi.goods.GoodsClient;
import fangxianyu.innerApi.goods.GoodsClientHandler;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.SimpleContent;
import io.github.nnkwrik.common.dto.SimpleGoods;
import io.github.nnkwrik.imservice.controller.ImServiceController;
import io.github.nnkwrik.imservice.dao.ChatMapper;
import io.github.nnkwrik.imservice.dao.HistoryMapper;
import io.github.nnkwrik.imservice.model.po.Chat;
import io.github.nnkwrik.imservice.model.po.History;
import io.github.nnkwrik.imservice.model.po.HistoryExample;
import io.github.nnkwrik.imservice.model.vo.ChatForm;
import io.github.nnkwrik.imservice.model.vo.ChatIndex;
import io.github.nnkwrik.imservice.model.vo.WsMessage;
import io.github.nnkwrik.imservice.redis.RedisClient;
import io.github.nnkwrik.imservice.service.impl.FormServiceImpl;
import io.github.nnkwrik.imservice.service.impl.IndexServiceImpl;
import io.github.nnkwrik.imservice.service.impl.WebSocketServiceImpl;
import io.github.nnkwrik.imservice.websocket.ChatEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ChatServiceTests {
    private ChatMapper chats;
    private HistoryMapper history;
    private RedisClient redis;
    private GoodsClient contents;
    private GoodsClientHandler goods;
    private UserClientHandler users;
    private ChatEndpoint endpoint;
    private FormServiceImpl form;
    private IndexServiceImpl index;
    private WebSocketServiceImpl socket;
    private Chat chat;
    private AtomicReference<List<WsMessage>> queued;
    private List<History> persisted;

    @Before
    public void setUp() {
        chats = mock(ChatMapper.class);
        history = mock(HistoryMapper.class);
        redis = mock(RedisClient.class);
        contents = mock(GoodsClient.class);
        goods = mock(GoodsClientHandler.class);
        users = mock(UserClientHandler.class);
        endpoint = mock(ChatEndpoint.class);
        form = new FormServiceImpl();
        index = new IndexServiceImpl();
        socket = new WebSocketServiceImpl();
        for (Object service : Arrays.asList(form, index, socket)) {
            ReflectionTestUtils.setField(service, "chatMapper", chats);
            ReflectionTestUtils.setField(service, "redisClient", redis);
        }
        for (Object service : Arrays.asList(form, index)) {
            ReflectionTestUtils.setField(service, "historyMapper", history);
            ReflectionTestUtils.setField(service, "goodsClient", contents);
            ReflectionTestUtils.setField(service, "goodsClientHandler", goods);
            ReflectionTestUtils.setField(service, "userClientHandler", users);
        }
        ReflectionTestUtils.setField(socket, "chatEndpoint", endpoint);
        chat = new Chat();
        chat.setId(9);
        chat.setU1("a");
        chat.setU2("b");
        chat.setGoodsId(0);
        chat.setPostId(42);
        when(chats.getChatById(9)).thenReturn(chat);
        when(chats.getChatIdsByUser("a")).thenReturn(Collections.singletonList(9));
        queued = new AtomicReference<>(new ArrayList<>());
        persisted = new CopyOnWriteArrayList<>();
        when(redis.<List<WsMessage>>get("9")).thenAnswer(call -> {
            assertTrue("Queue reads must share the writer's monitor", Thread.holdsLock(redis));
            return new ArrayList<>(queued.get());
        });
        doAnswer(call -> {
            assertTrue("Queue replacement must be inside the same monitor", Thread.holdsLock(redis));
            List<WsMessage> messages = call.getArgument(1);
            queued.set(new ArrayList<>(messages));
            return null;
        }).when(redis).set(eq("9"), any());
        doAnswer(call -> { persisted.addAll(call.getArgument(0)); return null; })
                .when(history).addHistoryList(anyList());
        when(history.getChatHistory(anyInt(), any(Date.class))).thenReturn(Collections.emptyList());
        when(history.getLastReadChat(anyList(), anyString(), nullable(Date.class))).thenReturn(Collections.emptyList());
        when(goods.getSimpleGoodsList(anyList())).thenReturn(Collections.emptyMap());
        when(users.getSimpleUserList(anyList())).thenReturn(Collections.emptyMap());
    }

    @After
    public void clearPage() {
        PageHelper.clearPage();
    }

    @Test
    public void protectsConversationReadsAndCalculatesEachMessagesDirection() {
        invalid(() -> form.showForm(9, "stranger", 10, new Date()));
        invalid(() -> form.flushUnread(9, "stranger"));
        invalid(() -> form.showForm(9, "a", 51, new Date()));
        verifyZeroInteractions(redis);
        queued.set(Arrays.asList(message("a", "b", "outgoing"), message("b", "a", "incoming")));
        List<History> combined = form.flushUnread(9, "a");
        assertEquals(Boolean.TRUE, combined.get(0).getU1ToU2());
        assertEquals(Boolean.FALSE, combined.get(1).getU1ToU2());
        assertEquals(1, persisted.size());
        assertEquals("incoming", persisted.get(0).getMessageBody());
        assertEquals(1, queued.get().size());
        assertEquals("outgoing", queued.get().get(0).getMessageBody());
    }

    @Test
    public void displaysContentAndLegacyGoodsCardsAndHandlesEmptyHistory() {
        SimpleContent content = new SimpleContent();
        content.setId(42);
        content.setKind("JOB");
        content.setTitle("招聘店员");
        when(contents.getSimpleContentList(Collections.singletonList(42)))
                .thenReturn(Response.ok(Collections.singletonMap(42, content)));
        ChatForm contentForm = form.showForm(9, "a", 10, new Date());
        assertSame(content, contentForm.getContent());
        assertNull(contentForm.getGoods());
        assertTrue(contentForm.getHistoryList().isEmpty());
        assertNotNull(contentForm.getOtherSide());
        chat.setPostId(0);
        chat.setGoodsId(7);
        SimpleGoods product = new SimpleGoods();
        product.setId(7);
        when(goods.getSimpleGoods(7)).thenReturn(product);
        ChatForm goodsForm = form.showForm(9, "b", 10, new Date());
        assertSame(product, goodsForm.getGoods());
        assertNull(goodsForm.getContent());
        assertFalse(goodsForm.getIsU1());
    }

    @Test
    public void countsIncomingMessagesEvenWhenTheLatestWasSentByCurrentUser() {
        WsMessage inbound = message("b", "a", "应聘咨询");
        WsMessage outbound = message("a", "b", "你好");
        inbound.setSendTime(new Date(1000));
        outbound.setSendTime(new Date(2000));
        when(redis.<List<WsMessage>>multiGet(anyList())).thenReturn(Collections.singletonList(Arrays.asList(inbound, outbound)));
        SimpleContent content = new SimpleContent();
        content.setId(42);
        when(contents.getSimpleContentList(Collections.singletonList(42)))
                .thenReturn(Response.ok(Collections.singletonMap(42, content)));
        HistoryExample old = new HistoryExample();
        old.setChatId(10);
        old.setU1("a");
        old.setU2("c");
        old.setGoodsId(7);
        old.setPostId(0);
        old.setSendTime(new Date(500));
        when(history.getLastReadChat(anyList(), eq("a"), nullable(Date.class))).thenReturn(Collections.singletonList(old));
        ChatIndex result = index.showIndex("a", 10, new Date());
        assertEquals(2, result.getChats().size());
        assertEquals(Integer.valueOf(1), result.getChats().get(0).getUnreadCount());
        assertSame(content, result.getChats().get(0).getContent());
        assertNotNull(result.getChats().get(1).getGoods());
        assertNotNull(result.getChats().get(1).getOtherSide());
        HistoryExample recentRead = new HistoryExample();
        recentRead.setChatId(9);
        recentRead.setU1("a");
        recentRead.setU2("b");
        recentRead.setGoodsId(0);
        recentRead.setPostId(42);
        recentRead.setSendTime(new Date(3000));
        recentRead.setMessageBody("newer read message");
        when(history.getLastReadChat(anyList(), eq("a"), nullable(Date.class))).thenReturn(Arrays.asList(old, recentRead));
        result = index.showIndex("a", 10, new Date());
        assertEquals(2, result.getChats().size());
        assertEquals("newer read message", result.getChats().get(0).getLastChat().getMessageBody());
        assertEquals(Integer.valueOf(1), result.getChats().get(0).getUnreadCount());
    }

    @Test
    public void validatesWebsocketMessagesAndUsesSessionParticipantsAndServerMetadata() throws Exception {
        WsMessage valid = message("a", "b", " hello ");
        valid.setGoodsId(999);
        valid.setPostId(999);
        valid.setSendTime(new Date(0));
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(valid));
        assertEquals(1, queued.get().size());
        WsMessage stored = queued.get().get(0);
        assertEquals(Integer.valueOf(0), stored.getGoodsId());
        assertEquals(Integer.valueOf(42), stored.getPostId());
        assertEquals("hello", stored.getMessageBody());
        assertTrue(stored.getSendTime().getTime() > 0);
        verify(chats).showToBoth(9);
        valid.setMessageType(2);
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(valid));
        valid.setMessageType(4);
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(valid));
        valid.setMessageType(1);
        valid.setReceiverId("stranger");
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(valid));
        socket.OnMessage("stranger", new ObjectMapper().writeValueAsString(valid));
        socket.OnMessage("a", "{invalid");
        valid.setReceiverId("b");
        valid.setMessageBody(String.join("", Collections.nCopies(2001, "x")));
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(valid));
        valid.setMessageBody("  ");
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(valid));
        assertEquals(1, queued.get().size());
        verify(endpoint, times(8)).sendMessage(anyString(), any(Response.class));
        valid.setMessageType(3);
        valid.setMessageBody("first chat");
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(valid));
        assertEquals(2, queued.get().size());
        verify(chats, times(2)).showToBoth(9);
    }

    @Test
    public void doesNotRemoveUnreadMessagesWhenHistoryPersistenceFails() {
        queued.set(Collections.singletonList(message("b", "a", "keep me")));
        doThrow(new IllegalStateException("database unavailable")).when(history).addHistoryList(anyList());
        try {
            form.flushUnread(9, "a");
            fail("Expected persistence failure");
        } catch (IllegalStateException expected) {
            assertEquals(1, queued.get().size());
            verify(redis, never()).set(anyString(), any());
        }
    }

    @Test
    public void acknowledgesOnlySavedMessagesAndCorrelatesFailures() throws Exception {
        WsMessage message = message("a", "b", "message");
        message.setClientMessageId("client-1");
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(message));
        ArgumentCaptor<Response> responses = ArgumentCaptor.forClass(Response.class);
        org.mockito.InOrder ordered = inOrder(redis, endpoint);
        ordered.verify(redis).set(eq("9"), any());
        ordered.verify(endpoint).sendMessage(eq("a"), responses.capture());
        WsMessage ack = (WsMessage) responses.getValue().getData();
        assertEquals(Integer.valueOf(5), ack.getMessageType());
        assertEquals("client-1", ack.getClientMessageId());
        assertEquals(Integer.valueOf(9), ack.getChatId());
        assertEquals(0, ack.getSendTime().getTime() % 1000);

        doThrow(new IllegalStateException("Redis unavailable")).when(redis).set(eq("9"), any());
        message.setClientMessageId("client-2");
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(message));
        message.setMessageType(5);
        message.setClientMessageId("client-3");
        socket.OnMessage("a", new ObjectMapper().writeValueAsString(message));
        socket.OnMessage("a", "{\"clientMessageId\":\"bad-json\",\"chatId\":[]}");
        verify(endpoint, times(4)).sendMessage(eq("a"), responses.capture());
        List<Response> sent = responses.getAllValues();
        Response failedSave = sent.get(sent.size() - 3);
        assertEquals(Response.UPDATE_HISTORY_TO_SQL_FAIL, failedSave.getErrno());
        assertEquals("client-2", ((Map) failedSave.getData()).get("clientMessageId"));
        assertEquals(Response.MESSAGE_IS_INCOMPLETE, sent.get(sent.size() - 2).getErrno());
        assertEquals("client-3", ((Map) sent.get(sent.size() - 2).getData()).get("clientMessageId"));
        assertEquals("bad-json", ((Map) sent.get(sent.size() - 1).getData()).get("clientMessageId"));
        assertEquals(1, queued.get().size());
    }

    @Test
    public void returnsTheCompleteBoundarySecondWithoutDuplicatingFlushedMessages() {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.addMapper(HistoryMapper.class);
        Map<String, Object> params = new HashMap<>();
        params.put("chatId", 9);
        params.put("start", new Date(2000));
        params.put("end", new Date(3000));
        String sql = configuration.getMappedStatement(HistoryMapper.class.getName() + ".getChatHistoryAtSecond")
                .getBoundSql(params).getSql();
        assertTrue(sql.contains("send_time >= ?"));
        assertTrue(sql.contains("send_time < ?"));
        History older = new History();
        older.setId(1);
        older.setChatId(9);
        older.setSendTime(new Date(1000));
        older.setMessageBody("older");
        persisted.add(older);
        List<WsMessage> messages = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            WsMessage item = message(i < 25 ? "b" : "a", i < 25 ? "a" : "b", "same-second-" + i);
            item.setSendTime(new Date(2000));
            messages.add(item);
        }
        queued.set(messages);
        doAnswer(call -> {
            List<History> incoming = call.getArgument(0);
            incoming.forEach(item -> {
                item.setId(persisted.size() + 1);
                persisted.add(item);
            });
            return null;
        }).when(history).addHistoryList(anyList());
        when(history.getChatHistory(eq(9), any(Date.class))).thenAnswer(call -> {
            Date cursor = call.getArgument(1);
            return persisted.stream().filter(item -> item.getSendTime().before(cursor))
                    .sorted(Comparator.comparing(History::getSendTime).thenComparing(History::getId).reversed())
                    .limit(20).collect(java.util.stream.Collectors.toList());
        });
        when(history.getChatHistoryAtSecond(eq(9), any(Date.class), any(Date.class))).thenAnswer(call -> {
            Date start = call.getArgument(1);
            Date end = call.getArgument(2);
            return persisted.stream().filter(item -> !item.getSendTime().before(start) && item.getSendTime().before(end))
                    .collect(java.util.stream.Collectors.toList());
        });
        ChatForm first = form.showForm(9, "a", 20, new Date(3000));
        assertEquals(28, first.getHistoryList().size());
        assertEquals(new Date(2000), first.getOffsetTime());
        assertEquals(28, first.getHistoryList().stream().map(History::getMessageBody).distinct().count());
        ChatForm second = form.showForm(9, "a", 20, first.getOffsetTime());
        assertEquals(1, second.getHistoryList().size());
        assertEquals("older", second.getHistoryList().get(0).getMessageBody());
        assertEquals(26, persisted.size());
        verify(history, times(1)).addHistoryList(anyList());
    }

    @Test
    public void concurrentSendingAndReadingDoesNotLoseMessages() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 24; i++) {
                final String body = "message-" + i;
                tasks.add(pool.submit(() -> {
                    try {
                        socket.OnMessage("b", new ObjectMapper().writeValueAsString(message("b", "a", body)));
                    } catch (Exception e) { throw new RuntimeException(e); }
                }));
                tasks.add(pool.submit(() -> form.flushUnread(9, "a")));
            }
            for (Future<?> task : tasks) task.get(10, TimeUnit.SECONDS);
            form.flushUnread(9, "a");
            assertTrue(queued.get().isEmpty());
            assertEquals(24, persisted.size());
            Set<String> bodies = new HashSet<>();
            persisted.forEach(item -> bodies.add(item.getMessageBody()));
            assertEquals(24, bodies.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void establishesGoodsAndContentChatsWithOnlyOneInitialHistoryEach() {
        ImServiceController controller = new ImServiceController();
        ReflectionTestUtils.setField(controller, "chatMapper", chats);
        ReflectionTestUtils.setField(controller, "historyMapper", history);
        doAnswer(call -> {
            Chat value = call.getArgument(0);
            value.setId(value.getPostId() > 0 ? 9 : 10);
            return null;
        }).when(chats).addChat(any(Chat.class));
        when(history.hasHistory(9)).thenReturn(false, true);
        controller.createContentChat(42, "a", "b");
        controller.createContentChat(42, "a", "b");
        controller.createChat(7, "a", "b");
        invalid(() -> controller.createContentChat(42, "a", "a"));
        ArgumentCaptor<History> initial = ArgumentCaptor.forClass(History.class);
        verify(history, times(2)).addHistory(initial.capture());
        initial.getAllValues().forEach(item -> {
            assertNotNull(item.getSendTime());
            assertEquals(Integer.valueOf(2), item.getMessageType());
        });
    }

    private WsMessage message(String sender, String receiver, String body) {
        WsMessage message = new WsMessage();
        message.setChatId(9);
        message.setSenderId(sender);
        message.setReceiverId(receiver);
        message.setGoodsId(0);
        message.setPostId(42);
        message.setMessageType(1);
        message.setMessageBody(body);
        message.setSendTime(new Date());
        return message;
    }

    private void invalid(Runnable action) {
        try {
            action.run();
            fail("Expected access or input rejection");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
