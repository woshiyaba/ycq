package io.github.nnkwrik.imservice.service.impl;

import io.github.nnkwrik.common.dto.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.nnkwrik.imservice.constant.MessageType;
import io.github.nnkwrik.imservice.dao.ChatMapper;
import io.github.nnkwrik.imservice.model.vo.WsMessage;
import io.github.nnkwrik.imservice.redis.RedisClient;
import io.github.nnkwrik.imservice.service.WebSocketService;
import io.github.nnkwrik.imservice.service.ChatAccess;
import io.github.nnkwrik.imservice.websocket.ChatEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Collections;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * @author nnkwrik
 * @date 18/12/05 12:30
 */
@Service
@Slf4j
public class WebSocketServiceImpl implements WebSocketService {
    @Autowired
    private ChatEndpoint chatEndpoint;

    @Autowired
    private ChatMapper chatMapper;

    @Autowired
    private RedisClient redisClient;


    /**
     * 未读消息数
     *
     * @param userId
     * @return
     */
    @Override
    public int getUnreadCount(String userId) {
        //去查userId参与的chat的id
        List<Integer> chatIdList = chatMapper.getChatIdsByUser(userId);
        if (chatIdList.isEmpty()) return 0;
        List<List<WsMessage>> unreadChats;
        synchronized (redisClient) {
            unreadChats = redisClient.multiGet(chatIdList.stream().map(String::valueOf).collect(Collectors.toList()));
        }

        //过滤自己发送的
        long unreadCount = unreadChats.stream()
                .filter(messageList -> !ObjectUtils.isEmpty(messageList))
                .flatMap(messageList -> messageList.stream())
                .filter(message -> userId.equals(message.getReceiverId()))
                .count();

        return Math.toIntExact(unreadCount);
    }

    /**
     * 对客户端发送的websocket消息做处理
     *
     * @param senderId
     * @param rawData
     */
    @Override
    public void OnMessage(String senderId, String rawData) {
        WsMessage message;
        String clientMessageId = null;
        try {
            if (rawData == null || rawData.length() > 20000) throw new IllegalArgumentException("消息格式不正确");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(rawData);
            if (node != null && node.hasNonNull("clientMessageId")) clientMessageId = node.get("clientMessageId").asText();
            message = mapper.treeToValue(node, WsMessage.class);
            if (message == null || message.getChatId() == null || message.getChatId() <= 0) {
                throw new IllegalArgumentException("请指定有效会话");
            }
            ChatAccess.validateMessage(chatMapper.getChatById(message.getChatId()), senderId, message);
            message.setMessageBody(message.getMessageBody().trim());
            message.setSendTime(new Date(System.currentTimeMillis() / 1000 * 1000));
        } catch (IOException e) {
            reject(senderId, Response.MESSAGE_FORMAT_IS_WRONG, "消息格式不正确", clientMessageId);
            return;
        } catch (IllegalArgumentException e) {
            reject(senderId, Response.MESSAGE_IS_INCOMPLETE, e.getMessage(), clientMessageId);
            return;
        } catch (RuntimeException e) {
            log.error("读取聊天会话失败", e);
            reject(senderId, Response.UPDATE_HISTORY_TO_SQL_FAIL, "会话读取失败，请重试", clientMessageId);
            return;
        }
        try {
            chatMapper.showToBoth(message.getChatId());
            updateRedis(message);
        } catch (RuntimeException e) {
            log.error("保存聊天消息失败，chatId={}", message.getChatId(), e);
            reject(senderId, Response.UPDATE_HISTORY_TO_SQL_FAIL, "消息保存失败，请重试", clientMessageId);
            return;
        }

        WsMessage ack = new WsMessage();
        ack.setMessageType(MessageType.MESSAGE_ACK);
        ack.setClientMessageId(clientMessageId);
        ack.setChatId(message.getChatId());
        ack.setSendTime(message.getSendTime());
        chatEndpoint.sendMessage(senderId, Response.ok(ack));

        //如果接收方在线,转发ws消息到接收方
        if (chatEndpoint.hasConnect(message.getReceiverId())) {
            chatEndpoint.sendMessage(message.getReceiverId(), Response.ok(message));
        }

    }

    private void updateRedis(WsMessage message) {
        synchronized (redisClient) {
            List<WsMessage> current = redisClient.get(message.getChatId() + "");
            List<WsMessage> unreadList = current == null ? new ArrayList<>() : new ArrayList<>(current);
            unreadList.add(message);
            redisClient.set(message.getChatId() + "", unreadList);
        }
    }

    private void reject(String senderId, int errno, String reason, String clientMessageId) {
        Response response = Response.fail(errno, reason);
        if (clientMessageId != null) response.setData(Collections.singletonMap("clientMessageId", clientMessageId));
        chatEndpoint.sendMessage(senderId, response);
    }

}
