package io.github.nnkwrik.imservice.service;

import io.github.nnkwrik.imservice.model.po.Chat;
import io.github.nnkwrik.imservice.model.vo.WsMessage;

public final class ChatAccess {
    private ChatAccess() { }

    public static void requireMember(Chat chat, String user) {
        if (chat==null || user==null || (!user.equals(chat.getU1()) && !user.equals(chat.getU2())))
            throw new IllegalArgumentException("会话不存在或无权访问");
    }

    public static void validateMessage(Chat chat, String sender, WsMessage message) {
        requireMember(chat,sender);
        if (message.getClientMessageId() != null && !message.getClientMessageId().matches("[A-Za-z0-9._:-]{1,80}"))
            throw new IllegalArgumentException("消息请求编号格式不正确");
        String receiver=sender.equals(chat.getU1()) ? chat.getU2() : chat.getU1();
        if (!sender.equals(message.getSenderId()) || !receiver.equals(message.getReceiverId()))
            throw new IllegalArgumentException("消息收发人不属于此会话");
        if (message.getMessageBody()==null || message.getMessageBody().trim().isEmpty() || message.getMessageBody().length()>2000)
            throw new IllegalArgumentException("消息需为1至2000字");
        if (message.getMessageType()==null || (message.getMessageType()!=1 && message.getMessageType()!=3))
            throw new IllegalArgumentException("消息类型不正确");
        message.setGoodsId(chat.getGoodsId());
        message.setPostId(chat.getPostId());
    }
}
