package io.github.nnkwrik.imservice.controller;

import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.imservice.constant.MessageType;
import io.github.nnkwrik.imservice.dao.ChatMapper;
import io.github.nnkwrik.imservice.dao.HistoryMapper;
import io.github.nnkwrik.imservice.model.po.Chat;
import io.github.nnkwrik.imservice.model.po.History;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 对于内部服务开放的api
 *
 * @author nnkwrik
 * @date 18/12/08 18:51
 */
@RestController
@Slf4j
@RequestMapping("/chat-service")
public class ImServiceController {

    @Autowired
    private ChatMapper chatMapper;

    @Autowired
    private HistoryMapper historyMapper;

    /**
     * 创建对话
     *
     * @param goodsId
     * @param senderId
     * @param receiverId
     * @return
     */
    @PostMapping("/createChat/{goodsId}/{senderId}/{receiverId}")
    @Transactional
    public Response<Integer> createChat(@PathVariable("goodsId") int goodsId,
                                        @PathVariable("senderId") String senderId,
                                        @PathVariable("receiverId") String receiverId) {
        if (goodsId <= 0) throw new IllegalArgumentException("商品不存在");
        return create(goodsId, 0, senderId, receiverId);
    }

    @PostMapping("/createContentChat/{postId}/{senderId}/{receiverId}")
    @Transactional
    public Response<Integer> createContentChat(@PathVariable int postId, @PathVariable String senderId,
                                               @PathVariable String receiverId) {
        if (postId <= 0) throw new IllegalArgumentException("内容不存在");
        return create(0, postId, senderId, receiverId);
    }

    private Response<Integer> create(int goodsId, int postId, String senderId, String receiverId) {
        if (senderId == null || receiverId == null || senderId.trim().isEmpty() || receiverId.trim().isEmpty()
                || senderId.length() > 32 || receiverId.length() > 32 || senderId.equals(receiverId)) {
            throw new IllegalArgumentException("请选择有效的聊天对象");
        }
        Chat chat = new Chat();
        chat.setGoodsId(goodsId);
        chat.setPostId(postId);
        if (senderId.compareTo(receiverId) < 0) {
            chat.setU1(senderId);
            chat.setU2(receiverId);
            chat.setShowToU1(true);
            chat.setShowToU2(false);
        } else {
            chat.setU1(receiverId);
            chat.setU2(senderId);
            chat.setShowToU1(false);
            chat.setShowToU2(true);
        }
        // The unique-key upsert holds this chat's row lock until the initial history is committed.
        chatMapper.addChat(chat);
        Integer chatId = chat.getId();
        if (!historyMapper.hasHistory(chatId)) {
            History history = new History();
            history.setChatId(chatId);
            history.setU1ToU2(senderId.compareTo(receiverId) < 0);
            history.setMessageType(MessageType.ESTABLISH_CHAT);
            history.setSendTime(new Date());
            historyMapper.addHistory(history);
        }


        log.info("创建聊天chatId={},发起人id={},接收方id={}", chatId, senderId, receiverId);
        return Response.ok(chatId);

    }
}
